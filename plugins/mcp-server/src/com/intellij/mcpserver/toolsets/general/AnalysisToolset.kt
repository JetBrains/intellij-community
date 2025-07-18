@file:Suppress("FunctionName", "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.toolsets.general

import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mcpserver.*
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.milliseconds

class AnalysisToolset : McpToolset {
  @McpTool
  @McpDescription("""
        |Analyzes the specified file for errors and warnings using IntelliJ's inspections.
        |Use this tool to identify coding issues, syntax errors, and other problems in a specific file.
        |Returns a list of problems found in the file, including severity, description, and location information.
        |Note: Only analyzes files within the project directory.
        |Note: Lines and Columns are 1-based.
    """)
  suspend fun get_file_problems(
    @McpDescription(Constants.RELATIVE_PATH_IN_PROJECT_DESCRIPTION)
    filePath: String,
    @McpDescription("Whether to include only errors or include both errors and warnings")
    errorsOnly: Boolean = true,
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
    timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE,
  ): FileProblemsResult {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.collecting.file.problems", filePath))
    val project = currentCoroutineContext().project
    val projectDir = project.projectDirectory

    val resolvedPath = project.resolveInProject(filePath)
    if (!resolvedPath.exists()) mcpFail("File not found: $filePath")
    if (!resolvedPath.isRegularFile()) mcpFail("Not a file: $filePath")

    val errors = CopyOnWriteArrayList<FileProblem>()
    val timedOut = withTimeoutOrNull(timeout.milliseconds) {
      withBackgroundProgress(
        project,
        McpServerBundle.message("progress.title.analyzing.file", resolvedPath.fileName),
        cancellable = true
      ) {
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath)
                   ?: mcpFail("Cannot access file: $filePath")
        readAction {
          val document = FileDocumentManager.getInstance().getDocument(file)
                         ?: mcpFail("Cannot read file: $filePath")

          DaemonCodeAnalyzerEx.processHighlights(
            document,
            project,
            if (errorsOnly) HighlightSeverity.ERROR else HighlightSeverity.WEAK_WARNING,
            0,
            document.textLength
          ) { highlightInfo ->
            errors.add(createFileProblem(document, highlightInfo))
            true
          }
        }
      }
    } == null

    return FileProblemsResult(
      filePath = projectDir.relativize(resolvedPath).pathString,
      errors = errors,
      timedOut = timedOut
    )
  }

  @McpTool
  @McpDescription("""
        |Retrieves all project problems (errors, warnings, etc.) detected in the project by IntelliJ's inspections.
        |Use this tool to get a comprehensive list of global project issues (compilation errors, inspection problems, etc.).
        |Does not require any parameters.
        |
        |Returns a list of problems with text, detailed description, and group information.
    """)
  suspend fun get_project_problems(
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
    timeout: Int = Constants.LONG_TIMEOUT_MILLISECONDS_VALUE,
  ): ProjectProblemsResult {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.checking.project.issues"))
    val project = currentCoroutineContext().project

    val problems = CopyOnWriteArrayList<ProjectProblem>()
    val timedOut = withTimeoutOrNull(timeout.milliseconds) {
      withBackgroundProgress(
        project,
        McpServerBundle.message("progress.title.collecting.project.problems"),
        cancellable = true
      ) {
        val collector = project.serviceAsync<ProblemsCollector>()
        val allProblems = collector.getProblemFiles().asSequence()
                            .flatMap {
                              collector.getFileProblems(it)
                            } + collector.getOtherProblems()
        for (problem in allProblems) {
          if (!coroutineContext.isActive) break
          problems.add(ProjectProblem(
            problemText = problem.text,
            group = problem.group,
            description = problem.description
          ))
        }
      }
    } == null

    return ProjectProblemsResult(
      problems = problems,
      timedOut = timedOut
    )
  }

  @McpTool
  @McpDescription("""
    |Get a list of all modules in the project with their types.
    |Returns structured information about each module including name and type.
  """)
  suspend fun get_project_modules(): ProjectModulesResult {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.listing.modules"))
    val project = currentCoroutineContext().project

    val modules = readAction {
      val moduleManager = ModuleManager.getInstance(project)
      moduleManager.modules.map { module ->
        ModuleInfo(
          name = module.name,
          type = module.moduleTypeName
        )
      }
    }

    return ProjectModulesResult(modules)
  }

  @McpTool
  @McpDescription("""
    |Get a list of all dependencies defined in the project.
    |Returns structured information about project library names.
  """)
  suspend fun get_project_dependencies(): ProjectDependenciesResult {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.checking.dependencies"))
    val project = currentCoroutineContext().project

    val dependencies = readAction {
      val moduleManager = ModuleManager.getInstance(project)
      moduleManager.modules.flatMap { module ->
        OrderEnumerator.orderEntries(module)
          .librariesOnly()
          .classes()
          .roots
          .map { root ->
            DependencyInfo(
              name = root.name
            )
          }
      }.distinctBy { it.name }
    }

    return ProjectDependenciesResult(dependencies)
  }

  private fun createFileProblem(
    document: Document,
    info: HighlightInfo,
  ): FileProblem {
    val startLine = document.getLineNumber(info.startOffset)
    val lineStartOffset = document.getLineStartOffset(startLine)
    val lineEndOffset = document.getLineEndOffset(startLine)
    val lineContent = document.getText(TextRange(lineStartOffset, lineEndOffset))
    val column = info.startOffset - lineStartOffset

    return FileProblem(
      severity = info.severity.name,
      description = info.description,
      lineContent = lineContent,
      line = startLine + 1, // Convert to 1-based
      column = column + 1 // Convert to 1-based
    )
  }

  @Serializable
  data class FileProblem(
    val severity: String,
    val description: String,
    val lineContent: String,
    val line: Int,
    val column: Int,
  )

  @Serializable
  data class FileProblemsResult(
    val filePath: String,
    val errors: List<FileProblem>,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val timedOut: Boolean = false,
  )

  @Serializable
  data class ProjectProblem(
    val problemText: String,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val group: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val description: String? = null,
  )

  @Serializable
  data class ProjectProblemsResult(
    val problems: List<ProjectProblem>,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val timedOut: Boolean = false,
  )

  @Serializable
  data class ModuleInfo(
    val name: String,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val type: String? = null,
  )

  @Serializable
  data class ProjectModulesResult(
    val modules: List<ModuleInfo>,
  )

  @Serializable
  data class DependencyInfo(
    val name: String
  )

  @Serializable
  data class ProjectDependenciesResult(
    val dependencies: List<DependencyInfo>,
  )
}
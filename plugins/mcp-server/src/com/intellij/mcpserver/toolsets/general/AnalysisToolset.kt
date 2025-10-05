@file:Suppress("FunctionName", "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.toolsets.general

import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.build.BuildViewProblemsService
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mcpserver.*
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jetbrains.concurrency.await
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.milliseconds

private val logger = logger<AnalysisToolset>()

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

  // IJPL-200264 Sometimes `build_project` tool reports as success=true and no errors, while Build toolwindow contains errors
  //@McpTool
  @McpDescription("""
      |Triggers building of the project, waits for completion, and returns build errors.
      |Use this tool to build the project and get detailed information about compilation errors and warnings.
      |The build will compile all modules in the project and return structured error information.
      |You have to use this tool after performing edits to validate if the edits are valid.
      |
      |If you see any unexpected errors after build you may try to call this tool again with `rebuild=true` parameter to perform full rebuild.
    """)
  suspend fun build_project(
    @McpDescription("Whether to perform full rebuild the project")
    rebuild: Boolean = false,
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
    timeout: Int = Constants.LONG_TIMEOUT_MILLISECONDS_VALUE,
  ): BuildProjectResult {
    currentCoroutineContext().reportToolActivity(if (rebuild) McpServerBundle.message("tool.activity.rebuilding.project")
                                                 else McpServerBundle.message("tool.activity.building.project"))
    val project = currentCoroutineContext().project
    val projectDirectory = project.projectDirectory

    val callId = currentCoroutineContext().mcpCallInfo.callId

    val problems = CopyOnWriteArrayList<ProjectProblem>()

    val buildResult = withTimeoutOrNull(timeout.milliseconds) {
      return@withTimeoutOrNull coroutineScope {
        val task = ProjectTaskManager.getInstance(project).createAllModulesBuildTask(!rebuild, project)
        val context = ProjectTaskContext(callId)
        return@coroutineScope ProjectTaskManager.getInstance(project).run(context, task).await()
      }
    }

    val problemsCollectionTimedOut = withTimeoutOrNull(timeout.milliseconds / 2) {
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
          val problem = if (problem is com.intellij.analysis.problemsView.FileProblem) {
            val kind = (problem as? BuildViewProblemsService.FileBuildProblem)?.event?.kind
            ProjectProblem(
              message = problem.text,
              group = problem.group,
              description = problem.description,
              kind = kind?.name,
              file = projectDirectory.relativizeIfPossible(problem.file),
              line = problem.line,
              column = problem.column,
            )
          }
          else {
            ProjectProblem(
              message = problem.text,
              group = problem.group,
              description = problem.description,
            )
          }
          problems.add(problem)
        }
      }
    } == null

    return BuildProjectResult(timedOut = buildResult == null || problemsCollectionTimedOut,
                              isSuccess = (buildResult != null && !buildResult.hasErrors()),
                              problems = problems)
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
    @property:McpDescription(Constants.TIMED_OUT_DESCRIPTION)
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val timedOut: Boolean? = false,
  )

  @Serializable
  enum class Kind {
    ERROR, WARNING, INFO, STATISTICS, SIMPLE
  }

  @Serializable
  data class ProjectProblem(
    val message: String,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val kind: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val group: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val description: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val file: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val line: Int? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val column: Int? = null,
  )

  @Serializable
  data class BuildProjectResult(
    @property:McpDescription("Whether the build was successful")
    val isSuccess: Boolean?,
    @property:McpDescription("A list of problems encountered during the build. May be empty if the build was successful.")
    val problems: List<ProjectProblem>,
    @property:McpDescription(Constants.TIMED_OUT_DESCRIPTION)
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val timedOut: Boolean? = false,
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
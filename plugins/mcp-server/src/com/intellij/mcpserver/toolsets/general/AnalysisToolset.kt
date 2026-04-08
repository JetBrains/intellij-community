@file:Suppress("FunctionName", "unused", "ReplaceGetOrSet")
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.toolsets.general

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpCallInfo
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskManager
import com.intellij.task.impl.ProjectTaskManagerImpl
import com.intellij.util.asDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jetbrains.concurrency.await
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

private val logger = logger<AnalysisToolset>()

class AnalysisToolset : McpToolset {
  @McpTool
  @McpDescription("""
        |Analyzes the specified files for errors and warnings using IntelliJ's inspections.
        |Use this tool to lint several files after editing them.
        |Returns per-file problems with severity, description, and location information.
        |`min_severity` must be `warning` or `error`; defaults to `warning`.
        |Note: Only analyzes files within the project directory.
        |Note: Lines and Columns are 1-based.
    """)
  suspend fun lint_files(
    @McpDescription("List of project-relative file paths to analyze. Duplicate paths are ignored after normalization.")
    file_paths: List<String>,
    @McpDescription("Minimum severity to include: `warning` or `error`. Defaults to `warning`.")
    min_severity: String = LintMinSeverity.WARNING.apiValue,
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
    timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE,
  ): LintFilesResult {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.collecting.file.problems.batch", file_paths.size))
    return collectLintFiles(
      filePaths = file_paths,
      minSeverityValue = min_severity,
      timeout = timeout,
      progressTitle = McpServerBundle.message("progress.title.analyzing.files", file_paths.size),
    )
  }

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
    val lintResult = collectLintFiles(
      filePaths = listOf(filePath),
      minSeverityValue = if (errorsOnly) LintMinSeverity.ERROR.apiValue else LintMinSeverity.WARNING.apiValue,
      timeout = timeout,
      progressTitle = McpServerBundle.message("progress.title.analyzing.file", filePath.substringAfterLast('/').substringAfterLast('\\')),
    )
    val item = lintResult.items.firstOrNull()
    val result = FileProblemsResult(
      filePath = item?.filePath ?: filePath,
      errors = item?.problems?.map { it.toLegacyFileProblem() }.orEmpty(),
      timedOut = item?.timedOut ?: lintResult.more,
    )
    logger.trace { "get_file_problems completed: timedOut=${result.timedOut}, errorsCount=${result.errors.size}" }
    return result
  }

  @McpTool
  @McpDescription("""
      |Triggers building of the project or specified files, waits for completion, and returns build errors.
      |Use this tool to build the project or compile files and get detailed information about compilation errors and warnings.
      |You have to use this tool after performing edits to validate if the edits are valid.
    """)
  suspend fun build_project(
    @McpDescription("Whether to perform full rebuild the project. Defaults to false. Effective only when `filesToRebuild` is not specified.")
    rebuild: Boolean = false,
    @McpDescription("If specified, only compile files with the specified paths. Paths are relative to the project root.")
    filesToRebuild: List<String>? = null,
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
    timeout: Int = Constants.LONG_TIMEOUT_MILLISECONDS_VALUE,
  ): BuildProjectResult {
    currentCoroutineContext().reportToolActivity(if (rebuild) McpServerBundle.message("tool.activity.rebuilding.project")
                                                 else McpServerBundle.message("tool.activity.building.project"))
    val project = currentCoroutineContext().project
    val projectDirectory = project.projectDirectory

    val callId = currentCoroutineContext().mcpCallInfo.callId

    val problems = CopyOnWriteArrayList<ProjectProblem>()
    val buildFinished = CompletableDeferred<Unit>()

    logger.trace { "Starting build task with timeout ${timeout}ms" }
    var buildStarted = false
    val buildResult = withTimeoutOrNull(timeout.milliseconds) {
      coroutineScope {
        val buildViewManager = project.serviceAsync<BuildViewManager>()
        // Listen to build events to collect problems directly
        buildViewManager.addListener(BuildProgressListener { buildId, event ->
          logger.trace { "Received build event: ${event.javaClass.simpleName}, buildId=$buildId" }

          when (event) {
            is StartBuildEvent -> {
              logger.trace { "Build started: ${event.buildDescriptor.title}" }
              buildStarted = true
            }

            is FileMessageEvent -> {
              // Collect file-based error/warning messages directly from build events
              if (event.kind == MessageEvent.Kind.ERROR || event.kind == MessageEvent.Kind.WARNING) {
                val filePosition = event.filePosition
                val virtualFile = filePosition.file?.let {
                  @Suppress("IO_FILE_USAGE")
                  VirtualFileManager.getInstance().findFileByNioPath(it.toPath())
                }

                val problem = ProjectProblem(
                  message = event.message,
                  kind = event.kind.name,
                  group = event.group,
                  description = event.description,
                  file = virtualFile?.let { projectDirectory.relativizeIfPossible(it) },
                  line = filePosition.startLine,
                  column = filePosition.startColumn,
                )

                logger.trace { "Collected problem from event: $problem" }
                problems.add(problem)
              }
            }

            is BuildIssueEvent -> {
              // Collect build issues (e.g., configuration problems, dependency issues)
              // BuildIssueEvent extends MessageEvent, so it has kind, group, etc.
              if (event.kind == MessageEvent.Kind.ERROR || event.kind == MessageEvent.Kind.WARNING) {
                val issue = event.issue
                val problem = ProjectProblem(
                  message = event.message,
                  kind = event.kind.name,
                  group = event.group,
                  description = event.description ?: issue.description,
                )

                logger.trace { "Collected build issue from event: $problem" }
                problems.add(problem)
              }
            }

            is FinishBuildEvent -> {
              val eventResult = event.result
              logger.trace { "Build finished: result=$eventResult" }
              // Extract failures from FinishBuildEvent result.
              // Some build systems (e.g., Bazel) report errors only through FailureResult
              // without emitting individual FileMessageEvent/BuildIssueEvent.
              if (eventResult is FailureResult) {
                for (failure in eventResult.failures) {
                  val message = failure.message ?: failure.description ?: "Build failure"
                  problems.add(ProjectProblem(
                    message = message,
                    kind = Kind.ERROR.name,
                    description = failure.description,
                  ))
                  logger.trace { "Collected failure from FinishBuildEvent: $message" }
                }
              }
              buildFinished.complete(Unit)
            }

            is MessageEvent -> {
              // Catch-all for MessageEvent subtypes not handled above (custom build system messages)
              if (event.kind == MessageEvent.Kind.ERROR || event.kind == MessageEvent.Kind.WARNING) {
                val problem = ProjectProblem(
                  message = event.message,
                  kind = event.kind.name,
                  group = event.group,
                  description = event.description,
                )
                logger.trace { "Collected generic message event: $problem" }
                problems.add(problem)
              }
            }
          }
        }, this.asDisposable())

        val task = if (!filesToRebuild.isNullOrEmpty()) {
          val filePaths = filesToRebuild.map { file -> project.resolveInProject(file) }
          logger.trace { "Refreshing files: $filePaths..." }
          LocalFileSystem.getInstance().refreshNioFiles(filePaths)
          val virtualFiles =
            filePaths.map { file -> LocalFileSystem.getInstance().findFileByNioFile(file) ?: mcpFail("File not found: $file") }
          logger.trace { "Creating build task for files: ${virtualFiles.joinToString { it.path }}" }
          readAction {
            (ProjectTaskManager.getInstance(project) as ProjectTaskManagerImpl).createModulesFilesTask(virtualFiles.toTypedArray())
          }
        }
        else {
          logger.trace { "Creating all modules build task, isIncrementalBuild=${!rebuild}" }
          readAction {
            ProjectTaskManager.getInstance(project).createAllModulesBuildTask(!rebuild, project)
          }
        }

        val context = ProjectTaskContext(callId)
        logger.trace { "Running build task with context" }

        // Run build and wait for FinishBuildEvent
        val result = ProjectTaskManager.getInstance(project).run(context, task).await()

        logger.trace { "Build task completed, waiting for FinishBuildEvent..." }
        if (buildStarted) {
          logger.trace { "Build was started, waiting for FinishBuildEvent" }
          buildFinished.await()
        }
        else {
          logger.trace { "Build was not started, skipping waiting for FinishBuildEvent" }
        }

        logger.trace { "FinishBuildEvent received" }
        result
      }
    }
    logger.trace { "Build completed: result=$buildResult, hasErrors=${buildResult?.hasErrors()}, problemsCount=${problems.size}" }

    logger.trace { "build_project completed: buildTimedOut=${buildResult == null}, problemsCount=${problems.size}" }
    // for the cases when the build doesn't report messages via BuildViewManager
    if (!buildStarted) {
      problems.add(ProjectProblem(message = "The project has limited build diagnostics functionality. Build messages cannot be collected."))
    }
    // Fallback: buildResult reports errors but no ERROR-level problems were captured through events
    val hasTaskErrors = buildResult?.hasErrors() == true
    if (hasTaskErrors && problems.none { it.kind == Kind.ERROR.name }) {
      problems.add(ProjectProblem(
        message = "Build reported errors, but detailed error messages were not captured through build events.",
        kind = Kind.ERROR.name,
      ))
    }
    return BuildProjectResult(
      timedOut = buildResult == null,
      isSuccess = buildResult != null && !hasTaskErrors && problems.none { it.kind == Kind.ERROR.name },
      problems = problems
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

  private suspend fun collectLintFiles(
    filePaths: List<String>,
    minSeverityValue: String,
    timeout: Int,
    progressTitle: @ProgressTitle String,
  ): LintFilesResult {
    val project = currentCoroutineContext().project
    val requestedFiles = prepareRequestedLintFiles(project, filePaths)
    val minSeverity = LintMinSeverity.parse(minSeverityValue)
    val request = LintFilesCollectorRequest(
      requestedFiles = requestedFiles,
      filePaths = requestedFiles.map { it.relativePath },
      minSeverity = minSeverity.highlightSeverity,
    )
    val completedResults = ConcurrentHashMap<String, LintFileResult>(requestedFiles.size)
    val completedFilePaths = ConcurrentHashMap.newKeySet<String>(requestedFiles.size)
    val onFileResult: (LintFileResult) -> Unit = { result ->
      completedFilePaths.add(result.filePath)
      if (result.problems.isNotEmpty()) {
        completedResults.putIfAbsent(result.filePath, result)
      }
    }

    val completedInTime = withTimeoutOrNull(timeout.milliseconds) {
      withBackgroundProgress(project, progressTitle, cancellable = true) {
        collectLintFiles(project, request, onFileResult)
      }
    } != null

    val items = requestedFiles.mapNotNull { requestedFile ->
      completedResults.get(requestedFile.relativePath)
    }
    val more = !completedInTime || completedFilePaths.size < requestedFiles.size

    logger.trace {
      "lint_files completed: more=$more, finishedInTime=$completedInTime, filesCount=${items.size}, completedCount=${completedFilePaths.size}, requestedCount=${requestedFiles.size}"
    }
    return LintFilesResult(items = items, more = more)
  }

  private fun LintProblem.toLegacyFileProblem(): FileProblem {
    return FileProblem(
      severity = severity,
      description = description,
      lineContent = lineText,
      line = line,
      column = column,
    )
  }

  @Serializable
  data class LintProblem(
    @JvmField val severity: String,
    @JvmField val description: String,
    @JvmField val lineText: String,
    @JvmField val line: Int,
    @JvmField val column: Int,
  )

  @Serializable
  data class LintFileResult(
    val filePath: String,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val problems: List<LintProblem> = emptyList(),
    @property:McpDescription(Constants.TIMED_OUT_DESCRIPTION)
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val timedOut: Boolean? = false,
  )

  @Serializable
  data class LintFilesResult(
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val items: List<LintFileResult> = emptyList(),
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val more: Boolean = false,
  )

  @Serializable
  data class FileProblem(
    @JvmField val severity: String,
    @JvmField val description: String,
    @JvmField val lineContent: String,
    @JvmField val line: Int,
    @JvmField val column: Int,
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
    @JvmField val name: String,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    @JvmField val type: String? = null,
  )

  @Serializable
  data class ProjectModulesResult(
    @JvmField val modules: List<ModuleInfo>,
  )

  @Serializable
  data class DependencyInfo(
    @JvmField val name: String,
  )

  @Serializable
  data class ProjectDependenciesResult(
    @JvmField val dependencies: List<DependencyInfo>,
  )
}

private enum class LintMinSeverity(
  @JvmField val apiValue: String,
  @JvmField val highlightSeverity: HighlightSeverity,
) {
  WARNING("warning", HighlightSeverity.WEAK_WARNING),
  ERROR("error", HighlightSeverity.ERROR);

  companion object {
    fun parse(value: String): LintMinSeverity {
      val normalized = value.trim().lowercase()
      return entries.firstOrNull { it.apiValue == normalized } ?: mcpFail("min_severity must be one of: warning, error")
    }
  }
}

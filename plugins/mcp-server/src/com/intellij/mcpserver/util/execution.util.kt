package com.intellij.mcpserver.util

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.lineMarker.RunLineMarkerContributor.Info
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.mcpCallInfo
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SyntaxTraverser
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.builder.text
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.containers.TreeTraversal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.nio.file.Files
import javax.swing.Action
import javax.swing.JComponent
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.time.Duration.Companion.milliseconds

private object RunConfigurationExecutionLogMarker
private val executionLogger = logger<RunConfigurationExecutionLogMarker>()

suspend fun checkUserConfirmationIfNeeded(@NlsContexts.Label notificationText: String, command: String?, project: Project) {

  fun rejected(): McpExpectedError = McpExpectedError("User rejected command execution")

  val commandExecutionMode = currentCoroutineContext().mcpCallInfo.mcpSessionOptions?.commandExecutionMode
  when (commandExecutionMode) {
    McpServerService.AskCommandExecutionMode.ASK -> {
      if (!askConfirmation(project, notificationText, command)) throw rejected()
    }
    McpServerService.AskCommandExecutionMode.RESPECT_GLOBAL_SETTINGS -> {
      if (!McpServerSettings.getInstance().state.enableBraveMode && !askConfirmation(project, notificationText, command)) {
        throw rejected()
      }
    }
    else -> {
      // do nothing
    }
  }
}

suspend fun askConfirmation(project: Project, @NlsContexts.Label notificationText: String, command: String?): Boolean {
  return withContext(Dispatchers.EDT) {
    val confirmationDialog = object : DialogWrapper(project, true) {
      init {
        init()
        title = McpServerBundle.message("dialog.title.confirm.command.execution")
        okAction.putValue(Action.NAME, McpServerBundle.message("command.execution.confirmation.allow"))
        cancelAction.putValue(Action.NAME, McpServerBundle.message("command.execution.confirmation.deny"))
      }

      override fun createCenterPanel(): JComponent {
        return panel {
          row {
            label(notificationText)
          }

          if (command != null) {
            row {
              textArea()
                .text(command)
                .align(Align.FILL)
                .rows(10)
                .applyToComponent {
                  lineWrap = true
                  isEditable = false
                  font = EditorFontType.getGlobalPlainFont()
                }
            }
          }
          row {
            checkBox(McpServerBundle.message("checkbox.enable.brave.mode.skip.command.execution.confirmations"))
              .bindSelected(McpServerSettings.getInstance().state::enableBraveMode)
              .comment(McpServerBundle.message("text.note.you.can.enable.brave.mode.in.settings.to.skip.this.confirmation"))
          }
        }
      }
    }
    confirmationDialog.show()
    return@withContext confirmationDialog.isOK
  }
}

fun prepareRunConfigurationForExecution(
  configurationName: String,
  configuration: RunConfiguration,
  programArguments: String?,
  workingDirectory: String?,
  envs: Map<String, String>?,
): RunConfiguration {
  val hasProgramArgumentsOverride = programArguments.hasNonEmptyOverride()
  val hasWorkingDirectoryOverride = workingDirectory.hasNonEmptyOverride()

  if (!hasProgramArgumentsOverride && !hasWorkingDirectoryOverride && envs == null) {
    return configuration
  }

  val copiedConfiguration = configuration.clone()
  val configurable = copiedConfiguration as? CommonProgramRunConfigurationParameters
                   ?: mcpFail(
    "Run configuration '$configurationName' of type '${configuration.type.displayName}' doesn't support dynamic launch overrides " +
    "(programArguments, workingDirectory, envs)."
  )

  if (hasProgramArgumentsOverride) {
    configurable.programParameters = programArguments.toEffectiveStringOverride()
  }
  if (hasWorkingDirectoryOverride) {
    configurable.workingDirectory = workingDirectory.toEffectiveStringOverride()
  }
  if (envs != null) {
    val mergedEnvs = LinkedHashMap(configurable.envs)
    mergedEnvs.putAll(envs)
    configurable.envs = mergedEnvs
  }

  return copiedConfiguration
}

private fun String?.hasNonEmptyOverride(): Boolean = !this.isNullOrEmpty()

private fun String?.toEffectiveStringOverride(): String? {
  require(!this.isNullOrEmpty())
  return this.takeUnless { it.isBlank() }
}

internal sealed interface RunConfigurationExecutionTarget {
  val presentableName: String

  data class ByName(val configurationName: String) : RunConfigurationExecutionTarget {
    override val presentableName: String
      get() = configurationName
  }

  data class ByContext(val filePath: String, val line: Int) : RunConfigurationExecutionTarget {
    override val presentableName: String
      get() = "$filePath:$line"
  }
}

internal data class ResolvedRunConfiguration(
  val settings: RunnerAndConfigurationSettings,
  // This may be a cloned configuration with one-shot launch overrides, not settings.configuration.
  val runConfiguration: RunConfiguration,
  val useOriginalSettings: Boolean,
)

internal data class RunConfigurationExecutionOutput(
  val exitCode: Int?,
  val output: String,
  val fullOutputPath: String?,
)

internal fun resolveRunConfigurationExecutionTarget(
  configurationName: String?,
  filePath: String?,
  line: Int?,
): RunConfigurationExecutionTarget {
  val normalizedConfigurationName = configurationName?.takeIf { it.isNotBlank() }
  val normalizedFilePath = filePath?.takeIf { it.isNotBlank() }
  val hasConfigurationName = normalizedConfigurationName != null
  val hasFilePath = normalizedFilePath != null
  val hasLine = line != null

  return when {
    hasConfigurationName && (hasFilePath || hasLine) ->
      mcpFail("Pass either configurationName or filePath + line, but not both.")
    hasConfigurationName ->
      RunConfigurationExecutionTarget.ByName(normalizedConfigurationName)
    hasFilePath && hasLine ->
      RunConfigurationExecutionTarget.ByContext(normalizedFilePath, line)
    hasFilePath || hasLine ->
      mcpFail("Pass both filePath and line together, or use configurationName.")
    else ->
      mcpFail("Pass either configurationName or filePath + line.")
  }
}

internal suspend fun resolveRunConfigurationForExecution(
  project: Project,
  executionTarget: RunConfigurationExecutionTarget,
  programArguments: String?,
  workingDirectory: String?,
  envs: Map<String, String>?,
) : ResolvedRunConfiguration {
  val settings = when (executionTarget) {
    is RunConfigurationExecutionTarget.ByName -> {
      readAction { RunManager.getInstance(project).allSettings.find { it.name == executionTarget.configurationName } }
      ?: mcpFail("Run configuration with name '${executionTarget.configurationName}' not found.")
    }
    is RunConfigurationExecutionTarget.ByContext ->
      readAction { resolveRunConfigurationFromCodeLocation(project, executionTarget.filePath, executionTarget.line) }
  }

  val runConfiguration = prepareRunConfigurationForExecution(
    configurationName = settings.name,
    configuration = settings.configuration,
    programArguments = programArguments,
    workingDirectory = workingDirectory,
    envs = envs,
  )
  val useOriginalSettings = runConfiguration === settings.configuration
  return ResolvedRunConfiguration(settings, runConfiguration, useOriginalSettings)
}

internal suspend fun executeResolvedRunConfiguration(
  project: Project,
  resolvedConfiguration: ResolvedRunConfiguration,
  timeout: Int,
  maxLinesCount: Int,
  truncateMode: TruncateMode,
): RunConfigurationExecutionOutput {
  val executor = DefaultRunExecutor.getRunExecutorInstance() ?: mcpFail("Execution is not supported in this environment or IDE")
  val exitCodeDeferred = CompletableDeferred<Int>()
  val outputBuilder = StringBuilder()

  withContext(Dispatchers.EDT) {
    val runner: ProgramRunner<*>? = ProgramRunner.getRunner(executor.id, resolvedConfiguration.runConfiguration)
    if (runner == null) mcpFail("No suitable runner found for configuration '${resolvedConfiguration.settings.name}'")

    val callback = createProcessCallback(exitCodeDeferred, outputBuilder)

    val environment = createExecutionEnvironment(
      project = project,
      executor = executor,
      runConfiguration = resolvedConfiguration.runConfiguration,
      useOriginalSettings = resolvedConfiguration.useOriginalSettings,
      runnerAndConfigurationSettings = resolvedConfiguration.settings,
    )
    environment.callback = callback
    runner.execute(environment)
  }

  val exitCode = awaitExitCode(exitCodeDeferred, timeout)
  val fullOutput = outputBuilder.toString()
  val output = truncateText(fullOutput, maxLinesCount = maxLinesCount, truncateMode = truncateMode)
  val fullOutputPath = if (output.length != fullOutput.length) createTmpFile(fullOutput) else null

  return RunConfigurationExecutionOutput(
    exitCode = exitCode,
    output = output,
    fullOutputPath = fullOutputPath,
  )
}

private fun createTmpFile(fullOutput: String): String? {
  return try {
    val tmpFile = Files.createTempFile(PathManager.getTempDir(), "run-config-output", ".log")
    Files.writeString(tmpFile, fullOutput)
    tmpFile.toAbsolutePath().toString()
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    executionLogger.trace { "Failed to write full output to temp file: ${e.message}" }
    null
  }
}

private fun createProcessCallback(
  exitCodeDeferred: CompletableDeferred<Int>,
  outputBuilder: StringBuilder,
): ProgramRunner.Callback = object : ProgramRunner.Callback {
  override fun processNotStarted(error: Throwable?) {
    exitCodeDeferred.completeExceptionally(
      error ?: IllegalStateException("Process not started by some reasons. Probably build process failed."))
  }

  override fun processStarted(descriptor: RunContentDescriptor) {
    val processHandler = descriptor.processHandler
    if (processHandler == null) {
      exitCodeDeferred.completeExceptionally(
        IllegalStateException("Process handler is null even though RunContentDescriptor exists."))
      return
    }

    processHandler.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        if (outputType == ProcessOutputTypes.SYSTEM) return
        outputBuilder.append(event.text)
      }

      override fun processTerminated(event: ProcessEvent) {
        exitCodeDeferred.complete(event.exitCode)
      }

      override fun processNotStarted() {
        exitCodeDeferred.completeExceptionally(
          IllegalStateException("Process explicitly reported as not started."))
      }
    })
    processHandler.startNotify()
  }
}

private suspend fun awaitExitCode(exitCodeDeferred: CompletableDeferred<Int>, timeout: Int): Int? {
  return withTimeoutOrNull(timeout.milliseconds) {
    try {
      exitCodeDeferred.await()
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      executionLogger.trace { "Execution failed: ${e.message}" }
      mcpFail("Execution failed: ${e.message}")
    }
  }
}

private fun createExecutionEnvironment(
  project: Project,
  executor: Executor,
  runConfiguration: RunConfiguration,
  useOriginalSettings: Boolean,
  runnerAndConfigurationSettings: RunnerAndConfigurationSettings,
) = if (useOriginalSettings) {
  // Reuse persisted settings when we are launching the original configuration instance.
  ExecutionEnvironmentBuilder.create(executor, runnerAndConfigurationSettings).build()
}
else {
  // Use the effective configuration directly when this launch uses a cloned configuration with overrides.
  ExecutionEnvironmentBuilder.create(project, executor, runConfiguration).build()
}

/**
 * Collects run points (executable entry points) in a file by iterating leaf PSI elements
 * and calling [RunLineMarkerContributor.getInfo] for each, same approach as
 * [com.intellij.execution.lineMarker.RunLineMarkerProvider.getLineMarkerInfo].
 */
fun collectRunPoints(psiFile: PsiFile, document: Document, project: Project): List<RunPoint> {
  val result = mutableMapOf<Int, RunPoint>()

  for (element in SyntaxTraverser.psiTraverser(psiFile).traverse(TreeTraversal.LEAVES_DFS)) {
    ProgressManager.checkCanceled()

    val contributors = DumbService.getInstance(project)
      .filterByDumbAwareness(RunLineMarkerContributor.EXTENSION.allForLanguageOrAny(element.language))

    var bestInfo: Info? = null
    for (contributor in contributors) {
      val info = contributor.getInfo(element) ?: continue
      if (bestInfo == null || info.shouldReplace(bestInfo)) {
        bestInfo = info
      }
    }

    if (bestInfo != null) {
      val line = document.getLineNumber(element.textOffset) + 1 // 1-based
      if (!result.containsKey(line)) {
        val configFromContext = findConfigurationFromContext(element) ?: continue
        val tooltip = try {
          bestInfo.tooltipProvider?.apply(element)
        }
        catch (_: Exception) {
          null
        }
        val elementText = try {
          UsageViewUtil.createNodeText(configFromContext.sourceElement).ifBlank { null }
        }
        catch (_: Exception) {
          null
        }
        result[line] = RunPoint(
          line = line,
          description = tooltip,
          elementText = elementText,
        )
      }
    }
  }

  return result.values.sortedBy { it.line }
}

/**
 * Walks up the PSI tree from the given element trying to find a [RunConfigurationProducer][com.intellij.execution.actions.RunConfigurationProducer]
 * that can create a run configuration from the [ConfigurationContext].
 */
fun findConfigurationFromContext(startElement: PsiElement): ConfigurationFromContext? {
  var element: PsiElement? = startElement
  while (element != null && element !is PsiFile) {
    ProgressManager.checkCanceled()
    val context = ConfigurationContext(element)
    val configs = context.configurationsFromContext
    if (!configs.isNullOrEmpty()) {
      return configs.first()
    }
    element = element.parent
  }
  return null
}

/**
 * Resolves a code location (file + line) into a temporary [RunnerAndConfigurationSettings]
 * by finding the appropriate [RunConfigurationProducer][com.intellij.execution.actions.RunConfigurationProducer]
 * for the PSI element at the specified line.
 *
 * Must be called inside a read action.
 */
fun resolveRunConfigurationFromCodeLocation(
  project: Project,
  filePath: String,
  line: Int,
): RunnerAndConfigurationSettings {
  val resolvedPath = project.resolveInProject(filePath)
  if (!resolvedPath.exists()) mcpFail("File not found: $filePath")
  if (!resolvedPath.isRegularFile()) mcpFail("Not a file: $filePath")
  if (line < 1) mcpFail("Line number must be >= 1, got: $line")

  val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath)
                    ?: mcpFail("Cannot access file: $filePath")
  val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                ?: mcpFail("Cannot find PSI file: $filePath")
  val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                 ?: mcpFail("Cannot get document: $filePath")

  val lineCount = document.lineCount
  if (line > lineCount) mcpFail("Line $line is out of range (file has $lineCount lines)")

  val lineStartOffset = document.getLineStartOffset(line - 1)
  val lineEndOffset = document.getLineEndOffset(line - 1)
  val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset))

  // Find first non-whitespace position on the line
  val offset = lineStartOffset + (lineText.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: 0)

  val psiElement = psiFile.findElementAt(offset)
                   ?: mcpFail("No code element found at $filePath:$line")

  val configFromContext = findConfigurationFromContext(psiElement)
                          ?: mcpFail("No run configuration could be created from $filePath:$line. " +
                                     "Use get_run_configurations with filePath to find valid run locations.")

  val settings = configFromContext.configurationSettings
  settings.isTemporary = true

  val runManager = RunManager.getInstance(project)
  runManager.setTemporaryConfiguration(settings)

  return settings
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RunPoint(
  @property:McpDescription("1-based line number of the executable code location.")
  val line: Int,
  @property:McpDescription("IDE-provided description or tooltip for this run point, when available.")
  @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
  val description: String? = null,
  @property:McpDescription("Short source snippet for the PSI element associated with this run point, when available.")
  @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
  val elementText: String? = null,
)

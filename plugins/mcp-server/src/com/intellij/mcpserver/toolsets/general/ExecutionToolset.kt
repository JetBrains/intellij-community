@file:Suppress("FunctionName", "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.toolsets.general

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.TruncateMode
import com.intellij.mcpserver.util.checkUserConfirmationIfNeeded
import com.intellij.mcpserver.util.prepareRunConfigurationForExecution
import com.intellij.mcpserver.util.truncateText
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.nio.file.Files
import kotlin.time.Duration.Companion.milliseconds

private val logger = logger<ExecutionToolset>()

class ExecutionToolset : McpToolset {

  @McpTool
  @McpDescription("""
    |Returns a list of run configurations for the current project.
    |Run configurations are usually used to define user the way how to run a user application, task or test suite from sources.
    |
    |This tool provides additional info like command line, working directory, environment variables,
    |and `supportsDynamicLaunchOverrides`, which is the source-of-truth capability flag for one-time launch overrides
    |(`programArguments`, `workingDirectory`, `envs`) in `execute_run_configuration` and `xdebug_start_debugger_session`.
    |Only pass those override parameters when this flag is `true` for the selected configuration.
    |
    |Use this tool to query the list of available run configurations in the current project.
  """)
  suspend fun get_run_configurations(): RunConfigurationsList {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.getting.run.configurations"))
    val project = currentCoroutineContext().project
    val runManager = RunManager.getInstance(project)

    val configurations = readAction {
      runManager.allSettings.map { configurationSettings ->
        val configuration = configurationSettings.configuration
        val runConfigurationParameters = configuration as? CommonProgramRunConfigurationParameters
        // TODO: render other details of other types of run configurations
        RunConfigurationInfo(
          name = configurationSettings.name,
          description = (configurationSettings.type.configurationTypeDescription ?: configurationSettings.type.displayName).ifBlank { null },
          supportsDynamicLaunchOverrides = runConfigurationParameters != null,
          commandLine = runConfigurationParameters?.programParameters?.ifBlank { null },
          workingDirectory = runConfigurationParameters?.workingDirectory?.ifBlank { null },
          environment = runConfigurationParameters?.envs?.ifEmpty { null },
        )
      }
    }
    return RunConfigurationsList(configurations)
  }

  @McpTool
  @McpDescription("""
    |Run a specific run configuration in the current project and wait up to specified timeout for it to finish.
    |Use this tool to run a run configuration that you have found from the "get_run_configurations" tool.
    |
    |Optional launch overrides (`programArguments`, `workingDirectory`, `envs`) are applied only for this run and are not persisted.
    |Do not pass these override parameters unless you explicitly need to change the configured launch values for this run.
    |Missing/null override parameters keep existing run configuration values unchanged.
    |For string overrides (`programArguments`, `workingDirectory`), only explicit empty string (`""`) clears an existing value.
    |
    |Returns the execution result including exit code, output, and success status.
  """)
  suspend fun execute_run_configuration(
    @McpDescription("Name of the run configuration to execute")
    configurationName: String,
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
    timeout: Int = Constants.LONG_TIMEOUT_MILLISECONDS_VALUE,
    @McpDescription(Constants.MAX_LINES_COUNT_DESCRIPTION)
    maxLinesCount: Int = Constants.MAX_LINES_COUNT_VALUE,
    @McpDescription(Constants.TRUNCATE_MODE_DESCRIPTION)
    truncateMode: TruncateMode = Constants.TRUCATE_MODE_VALUE,
    @McpDescription("Optional program arguments override for this launch only. Missing/null keeps existing value; explicit empty string clears it.")
    programArguments: String? = null,
    @McpDescription("Optional working directory override for this launch only. Missing/null keeps existing value; explicit empty string clears it.")
    workingDirectory: String? = null,
    @McpDescription("Optional environment variable overrides for this launch only. Missing/null keeps existing env unchanged; when provided, values are merged over existing env.")
    envs: Map<String, String>? = null,
    ): RunConfigurationResult {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.executing.run.configuration", configurationName))
    val project = currentCoroutineContext().project
    val runManager = RunManager.getInstance(project)

    val runnerAndConfigurationSettings = readAction { runManager.allSettings.find { it.name == configurationName } } ?: mcpFail("Run configuration with name '$configurationName' not found.")
    val runConfiguration = prepareRunConfigurationForExecution(
      configurationName = configurationName,
      configuration = runnerAndConfigurationSettings.configuration,
      programArguments = programArguments,
      workingDirectory = workingDirectory,
      envs = envs,
    )
    val useOriginalSettings = runConfiguration === runnerAndConfigurationSettings.configuration

    val runConfigurationParameters = (runConfiguration as? CommonProgramRunConfigurationParameters)?.programParameters
    val notificationText = if (runConfigurationParameters != null) {
      McpServerBundle.message("label.do.you.want.to.execute.run.configuration.with.command", configurationName)
    }
    else {
      McpServerBundle.message("label.do.you.want.to.execute.run.configuration", configurationName)
    }
    checkUserConfirmationIfNeeded(notificationText, command = runConfigurationParameters, project)
    val executor = DefaultRunExecutor.getRunExecutorInstance() ?: mcpFail("Execution is not supported in this environment or IDE")

    val exitCodeDeferred = CompletableDeferred<Int>()
    val outputBuilder = StringBuilder()

    withContext(Dispatchers.EDT) {
      val runner: ProgramRunner<*>? = ProgramRunner.getRunner(executor.id, runConfiguration)
      if (runner == null) mcpFail("No suitable runner found for configuration '${runnerAndConfigurationSettings.name}'")

      val callback = object : ProgramRunner.Callback {

        override fun processNotStarted(error: Throwable?) {
          exitCodeDeferred.completeExceptionally(error ?: IllegalStateException("Process not started by some reasons. Probably build process failed."))
        }
        override fun processStarted(descriptor: RunContentDescriptor) {
          val processHandler = descriptor.processHandler
          if (processHandler == null) {
            exitCodeDeferred.completeExceptionally(IllegalStateException("Process handler is null even though RunContentDescriptor exists."))
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
              exitCodeDeferred.completeExceptionally(IllegalStateException("Process explicitly reported as not started."))
            }
          })
          processHandler.startNotify()
        }
      }

      val environment = createExecutionEnvironment(
        project = project,
        executor = executor,
        runConfiguration = runConfiguration,
        useOriginalSettings = useOriginalSettings,
        runnerAndConfigurationSettings = runnerAndConfigurationSettings,
      )
      environment.callback = callback
      runner.execute(environment)
    }

    val exitCode = withTimeoutOrNull(timeout.milliseconds) {
      try {
        exitCodeDeferred.await()
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        logger.trace { "Execution failed: ${e.message}" }
        mcpFail("Execution failed: ${e.message}")
      }
    }
    val fullOutput = outputBuilder.toString()
    val output = truncateText(fullOutput, maxLinesCount = maxLinesCount, truncateMode = truncateMode)
    val fullOutputPath = if (output.length != fullOutput.length) createTmpFile(fullOutput) else null

    return RunConfigurationResult(
      exitCode = exitCode,
      timedOut = exitCode == null,
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
      logger.trace { "Failed to write full output to temp file: ${e.message}" }
      null
    }
  }

  private fun createExecutionEnvironment(
    project: com.intellij.openapi.project.Project,
    executor: Executor,
    runConfiguration: RunConfiguration,
    useOriginalSettings: Boolean,
    runnerAndConfigurationSettings: com.intellij.execution.RunnerAndConfigurationSettings,
  ) = if (useOriginalSettings) {
    ExecutionEnvironmentBuilder.create(executor, runnerAndConfigurationSettings).build()
  }
  else {
    ExecutionEnvironmentBuilder.create(project, executor, runConfiguration).build()
  }

  @Serializable
  data class RunConfigurationsList(
    val configurations: List<RunConfigurationInfo>,
  )

  @Serializable
  data class RunConfigurationInfo(
    val name: String,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val description: String? = null,
    @property:McpDescription("Whether this run configuration supports one-time dynamic launch overrides for `programArguments`, `workingDirectory`, and `envs`. Use this field as the source of truth before passing those override parameters to `execute_run_configuration` or `xdebug_start_debugger_session`.")
    val supportsDynamicLaunchOverrides: Boolean,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val commandLine: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val workingDirectory: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val environment: Map<String, String>? = null
  )

  @Serializable
  data class RunConfigurationResult @JvmOverloads constructor(
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val exitCode: Int? = null,
    @property:McpDescription(Constants.TIMED_OUT_DESCRIPTION)
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val timedOut: Boolean? = false,
    val output: String,
    @property:McpDescription("Path to a temp file containing the full untruncated output. Present only when output was truncated. Use read_file to access the complete log.")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val fullOutputPath: String? = null,
  )
}

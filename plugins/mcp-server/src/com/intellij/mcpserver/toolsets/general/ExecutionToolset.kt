@file:Suppress("FunctionName", "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.toolsets.general

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.mcpserver.*
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.TruncateMode
import com.intellij.mcpserver.util.checkUserConfirmationIfNeeded
import com.intellij.mcpserver.util.truncateText
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Key
import kotlinx.coroutines.*
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

class ExecutionToolset : McpToolset {

  @McpTool
  @McpDescription("""
    |Returns a list of run configurations for the current project.
    |Run configurations are usually used to define user the way how to run a user application, task or test suite from sources.
    |
    |This tool provides additional info like command line, working directory, and environment variables if they are available.
    |
    |Use this tool to query the list of available run configurations in the current project.
  """)
  suspend fun get_run_configurations(): RunConfigurationsList {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.getting.run.configurations"))
    val project = currentCoroutineContext().project
    val runManager = RunManager.getInstance(project)

    val configurations = readAction {
      runManager.allSettings.map { configurationSettings ->
        val runConfigurationParameters = configurationSettings.configuration as? CommonProgramRunConfigurationParameters
        // TODO: render other details of other types of run configurations
        RunConfigurationInfo(
          name = configurationSettings.name,
          description = (configurationSettings.type.configurationTypeDescription ?: configurationSettings.type.displayName).ifBlank { null },
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
    ): RunConfigurationResult {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.executing.run.configuration", configurationName))
    val project = currentCoroutineContext().project
    val runManager = RunManager.getInstance(project)

    val runnerAndConfigurationSettings = readAction { runManager.allSettings.find { it.name == configurationName } } ?: mcpFail("Run configuration with name '$configurationName' not found.")

    val runConfigurationParameters = (runnerAndConfigurationSettings.configuration as? CommonProgramRunConfigurationParameters)?.programParameters
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
      val runner: ProgramRunner<*>? = ProgramRunner.getRunner(executor.id, runnerAndConfigurationSettings.configuration)
      if (runner == null) mcpFail("No suitable runner found for configuration '${runnerAndConfigurationSettings.name}'")

      val callback = object : ProgramRunner.Callback {
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

      val environment = ExecutionEnvironmentBuilder.create(project, executor, runnerAndConfigurationSettings.configuration).build()
      environment.callback = callback
      runner.execute(environment)
    }

    val exitCode = withTimeoutOrNull(timeout.milliseconds) {
      exitCodeDeferred.await()
    }
    val output = truncateText(outputBuilder.toString(), maxLinesCount = maxLinesCount, truncateMode = truncateMode)
    return RunConfigurationResult(
      exitCode = exitCode,
      timedOut = exitCode == null,
      output = output
    )
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
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val commandLine: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val workingDirectory: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val environment: Map<String, String>? = null
  )

  @Serializable
  data class RunConfigurationResult(
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val exitCode: Int? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val timedOut: Boolean = false,
    val output: String
  )
}
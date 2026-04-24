@file:Suppress("FunctionName", "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.toolsets.general

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.RunManager
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.RunPoint
import com.intellij.mcpserver.util.collectRunPoints
import com.intellij.mcpserver.util.executeRunConfiguration
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

class ExecutionToolset : McpToolset {

  @McpTool
  @McpDescription("""
    |Returns either project run configurations or executable code locations, depending on the input.
    |
    |Without `filePath`, this tool lists the project's existing run configurations. The result includes configuration
    |names and, when available, launch details such as program arguments, working directory, environment variables,
    |and `supportsDynamicLaunchOverrides`.
    |
    |`supportsDynamicLaunchOverrides` is the source-of-truth capability flag for one-time launch overrides
    |(`programArguments`, `workingDirectory`, `envs`) in `execute_run_configuration` and `xdebug_start_debugger_session`.
    |Only pass those override parameters when this flag is `true` for the selected configuration.
    |
    |With `filePath`, this tool discovers executable entry points (run points) in that file, such as test methods,
    |main methods, or other executable entry points where the IDE shows a Run gutter icon. The result contains `filePath` and
    |`runPoints`; use the returned line numbers with `execute_run_configuration` to run from code.
  """)
  suspend fun get_run_configurations(
    @McpDescription("Optional file path relative to the project root. When provided, returns run points (executable entry points) in the file instead of project-wide run configurations.")
    filePath: String? = null,
  ): GetRunConfigurationsResult {
    val project = currentCoroutineContext().project

    if (filePath != null) {
      currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.discovering.run.points", filePath))

      val resolvedPath = project.resolveInProject(filePath)
      if (!resolvedPath.exists()) mcpFail("File not found: $filePath")
      if (!resolvedPath.isRegularFile()) mcpFail("Not a file: $filePath")

      val runPoints = readAction {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath)
                          ?: mcpFail("Cannot access file: $filePath")
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                      ?: mcpFail("Cannot find PSI file: $filePath")
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                       ?: mcpFail("Cannot get document: $filePath")

        collectRunPoints(psiFile, document, project)
      }

      return GetRunConfigurationsResult(filePath = filePath, runPoints = runPoints)
    }

    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.getting.run.configurations"))
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
    return GetRunConfigurationsResult(configurations = configurations)
  }

  @McpTool
  @McpDescription("""
    |Run either an existing run configuration by name or a temporary run configuration created from a code location
    |(`filePath` + `line`) in the current project, then wait up to specified timeout for it to finish.
    |Use this tool with either a configuration name returned by `get_run_configurations`, or with a run point
    |(`filePath` + `line`) returned by `get_run_configurations(filePath = ...)`.
    |
    |Optional launch overrides (`programArguments`, `workingDirectory`, `envs`) are applied only for this run and are not persisted.
    |Do not pass these override parameters unless you explicitly need to change the configured launch values for this run.
    |Missing/null override parameters keep existing run configuration values unchanged.
    |For string overrides (`programArguments`, `workingDirectory`), missing/null or empty string (`""`) keeps the existing value unchanged.
    |Pass a whitespace-only string such as `" "` to clear an existing value for this launch.
    |
    |Pass either `configurationName`, or `filePath` together with `line`. These modes are mutually exclusive.
    |
    |Behavior:
    |- When `waitForExit=true`, waits up to `timeout` milliseconds for process termination. If the timeout expires,
    |  the process keeps running in the background and `exitCode` is omitted from the result.
    |- When `waitForExit=false`, waits only for the process to start, then returns immediately without applying `timeout`.
    |- `fullOutputPath` points to a temp file with the full raw output and may continue growing while the process is alive.
    |
    |Returns the execution result including current output snapshot, optional exit code, and optional `fullOutputPath`.
  """)
  suspend fun execute_run_configuration(
    @McpDescription("Name of the existing run configuration to execute")
    configurationName: String? = null,
    @McpDescription("File path relative to the project root. Provide together with `line` to create and execute a temporary run configuration from code context.")
    filePath: String? = null,
    @McpDescription("1-based line number for `filePath`. Provide together with `filePath` and do not combine with `configurationName`.")
    line: Int? = null,
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
    timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE,
    @McpDescription("Whether to wait for process termination. If false, the tool returns immediately after the process starts and ignores `timeout`.")
    waitForExit: Boolean = true,
    @McpDescription("Optional program arguments override for this launch only. Missing/null or empty string keeps the existing value; whitespace-only string clears it.")
    programArguments: String? = null,
    @McpDescription("Optional working directory override for this launch only. Missing/null or empty string keeps the existing value; whitespace-only string clears it.")
    workingDirectory: String? = null,
    @McpDescription("Optional environment variable overrides for this launch only. Missing/null keeps existing env unchanged; when provided, values are merged over existing env.")
    envs: Map<String, String>? = null,
  ): RunConfigurationResult {
    return executeRunConfiguration(
      configurationName = configurationName,
      filePath = filePath,
      line = line,
      timeout = timeout,
      waitForExit = waitForExit,
      programArguments = programArguments,
      workingDirectory = workingDirectory,
      envs = envs,
      isDebug = false,
    ).let { executionOutput ->
      RunConfigurationResult(
        exitCode = executionOutput.exitCode,
        output = executionOutput.output,
        fullOutputPath = executionOutput.outputPath?.pathString,
        sessionId = null, // reset sessionId because at the moment it's unnecessary for execution (not tools can use it)
      )
    }
  }

  @Serializable
  data class GetRunConfigurationsResult(
    @property:McpDescription("Project run configurations. Present when the tool is called without `filePath`.")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val configurations: List<RunConfigurationInfo>? = null,
    @property:McpDescription("File path relative to the project root for which `runPoints` were collected. Present only when the tool is called with `filePath`.")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val filePath: String? = null,
    @property:McpDescription("Executable entry points discovered in `filePath`, such as test methods, main methods, or other executable entry points. Present only when the tool is called with `filePath`.")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val runPoints: List<RunPoint>? = null,
  )

  @Serializable
  data class RunConfigurationInfo(
    @property:McpDescription("Run configuration name. Pass this value as `configurationName` to `execute_run_configuration`.")
    val name: String,
    @property:McpDescription("Human-readable run configuration type or description shown by the IDE, when available.")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val description: String? = null,
    @property:McpDescription("Whether this run configuration supports one-time dynamic launch overrides for `programArguments`, `workingDirectory`, and `envs`. Use this field as the source of truth before passing those override parameters to `execute_run_configuration` or `xdebug_start_debugger_session`.")
    val supportsDynamicLaunchOverrides: Boolean,
    @property:McpDescription("Configured command line or program arguments for this run configuration, when available.")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val commandLine: String? = null,
    @property:McpDescription("Configured working directory for this run configuration, when available.")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val workingDirectory: String? = null,
    @property:McpDescription("Configured environment variables for this run configuration, when available.")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val environment: Map<String, String>? = null
  )

  @Serializable
  data class RunConfigurationResult @JvmOverloads constructor(
    @property:McpDescription("Process exit code. Absent when the tool returns before observing process termination, for example when `waitForExit=false` or when `timeout` expires.")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val exitCode: Int? = null,

    @Deprecated("To be removed")
    @property:McpDescription("Deprecated")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val timedOut: Boolean? = false,

    @property:McpDescription("Captured process output snapshot. The snapshot includes up to the first ${Constants.RUN_CONFIGURATION_PREVIEW_MAX_LENGTH} characters of process output; when additional output exists, `<truncated>` is appended to the preview.")
    val output: String,

    @property:McpDescription("Path to a temp file containing the full raw output. The file may continue growing while the process is still running and remains available while the IDE is running.")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val fullOutputPath: String? = null,

    @property:McpDescription("Session identifier for this run. Uses the session name by default; if duplicate live run sessions exist, format is `<sessionName>#<executionId>`.")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val sessionId: String? = null,
  )
}

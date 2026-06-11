// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.terminal

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.python.terminal.shared.PyTerminalBundle
import com.intellij.python.terminal.shared.PyVirtualEnvTerminalSettings
import com.jetbrains.python.PyNames
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.Activatable
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.pyRichSdk
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.terminal.Shell
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer
import java.nio.file.Path
import kotlin.io.path.isExecutable


private data class Jediterm(val source: String, val sourceArgs: List<String>? = null) {
  private companion object {
    const val JEDITERM_SOURCE = "JEDITERM_SOURCE"
    const val JEDITERM_SOURCE_ARGS = "JEDITERM_SOURCE_ARGS"

    // **nix shell integration scripts split arguments;
    // since a path may contain spaces, we do not want it to be split into several arguments.
    const val JEDITERM_SOURCE_SINGLE_ARG = "JEDITERM_SOURCE_SINGLE_ARG"
  }

  constructor(path: Path, args: List<String>? = null) : this(path.toAbsolutePath().toString(), args)
  constructor(activateScript: Activatable.Script) : this(activateScript.scriptPath, activateScript.args)

  fun buildEnvironmentVariables(): Map<String, String> = buildMap {
    put(JEDITERM_SOURCE, source)
    // Only emit the args vars when there actually are arguments. An empty JEDITERM_SOURCE_ARGS is
    // meaningless, and since these vars are now force-set via `_INTELLIJ_FORCE_SET_*`, an empty
    // `_INTELLIJ_FORCE_SET_JEDITERM_SOURCE_ARGS` breaks the PowerShell integration on Windows:
    // Get-ChildItem lists the empty-valued var, but Remove-Item reports it as non-existent (PY-90240).
    sourceArgs?.takeIf { it.isNotEmpty() }?.let {
      put(JEDITERM_SOURCE_ARGS, it.joinToString(" "))
      put(JEDITERM_SOURCE_SINGLE_ARG, "1")
    }
  }
}

class PyVirtualEnvTerminalCustomizer : ShellExecOptionsCustomizer {
  private companion object {
    val logger = fileLogger()
  }

  /**
   *``conda init`` installs conda activation hook into user profile
   *  We run this hook manually because we can't ask user to install hook and restart terminal
   *  In case of failure we ask user to run "conda init" manually
   */
  private fun PythonEnvironment.Conda.getPowerShellActivationCommand(): Jediterm {
    val condaPath = condaExecutable?.takeIf { it.isExecutable() }

    if (condaPath == null) {
      logger.warn("Can't find $condaExecutable, will not activate conda")
      val message = PyTerminalBundle.message("powershell.conda.not.activated", "conda")
      return Jediterm("echo '$message'")
    }

    // ' are in "Write-Host"
    val errorMessage = PyTerminalBundle.message("powershell.conda.not.activated", condaPath).replace('\'', '"')


    // No need to escape path: conda can't have spaces
    return Jediterm(
      """
        & '${StringUtil.escapeChar(condaPath.toString(), '\'')}' shell.powershell hook | Out-String | Invoke-Expression ;
        try {
          conda activate '${StringUtil.escapeChar(pythonHomePath.toString(), '\'')}'
        } catch {
          Write-Host('${StringUtil.escapeChar(errorMessage, '\'')}')
        }
        """.trimIndent()
    )
  }

  private fun activateUnknownShell(sdk: Sdk, envs: MutableMap<String, String>): Jediterm? {
    //for other shells we read envs from activate script by the default shell and pass them to the process
    val envVars = PySdkUtil.activateVirtualEnv(sdk)
    if (envVars.isEmpty()) {
      logger.warn("No vars found to activate in ${sdk.homePath}")
    }
    else {
      envs.putAll(envVars)
    }
    return null
  }

  private fun activateVenv(shell: Shell, sdk: Sdk, venv: PythonEnvironment.Venv, envs: MutableMap<String, String>): Jediterm? {
    return when (shell.type) {
      Shell.Type.UNKNOWN -> activateUnknownShell(sdk, envs)
      else -> venv.activation.invoke(shell.type)?.let { Jediterm(it) }
    }
  }

  private fun activateConda(shell: Shell, sdk: Sdk, condaEnv: PythonEnvironment.Conda, envs: MutableMap<String, String>): Jediterm? {
    return when (shell.type) {
      Shell.Type.POWERSHELL -> condaEnv.getPowerShellActivationCommand()
      Shell.Type.BASH, Shell.Type.SH, Shell.Type.ZSH, Shell.Type.FISH, Shell.Type.CSH -> {
        condaEnv.activation.invoke(shell.type)?.let { Jediterm(it) }
      }
      Shell.Type.UNKNOWN -> activateUnknownShell(sdk, envs)
    }
  }

  override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
    val activationEnvs = LinkedHashMap<String, String>()
    customizeEnvironment(
      project = project,
      workingDirectory = shellExecOptions.workingDirectory.asNioPath().toString(),
      command = shellExecOptions.execCommand.command.toTypedArray(),
      envs = activationEnvs,
      terminalEelDescriptor = shellExecOptions.eelDescriptor,
    )
    for ((name, value) in activationEnvs) {
      shellExecOptions.setEnvironmentVariable(name, value)
    }
  }

  override fun getDefaultStartWorkingDirectory(project: Project): Path? {
    val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return null
    return pyTerminalDefaultWorkingDirectory(project, file)
  }

  @Deprecated(
    "Use `customizeEnvironment` instead",
    ReplaceWith("customizeEnvironment(project, workingDirectory, command, envs, terminalEelDescriptor)")
  )
  fun customizeCommandAndEnvironment(
    project: Project,
    workingDirectory: String?,
    command: Array<out String>,
    envs: MutableMap<String, String>,
  ): Array<out String> {
    customizeEnvironment(project, workingDirectory, command, envs, project.getEelDescriptor())
    return command
  }

  /**
   * Computes the virtual env activation for a shell running [command] in [workingDirectory], writing the
   * activation environment variables into [envs]. The SDK is taken from the module that owns [workingDirectory].
   *
   * When [terminalEelDescriptor] is provided, activation is skipped if the SDK lives in a different environment
   * than the terminal (e.g. a Windows interpreter for a WSL shell), to avoid injecting cross-environment paths.
   */
  @VisibleForTesting
  @ApiStatus.Experimental
  fun customizeEnvironment(
    project: Project,
    workingDirectory: String?,
    command: Array<out String>,
    envs: MutableMap<String, String>,
    terminalEelDescriptor: EelDescriptor,
  ) {
    if (!TerminalOptionsProvider.instance.shellIntegration) {
      logger.debug("Shell integration is disabled, skipping virtual env activation for ${project.name}")
      return
    }

    if (!PyVirtualEnvTerminalSettings.getInstance(project).virtualEnvActivate) {
      logger.debug("Virtual env activation is disabled for ${project.name}")
      return
    }

    val shell = Shell.resolve(command)
    if (shell == null) {
      logger.warn("No shell to run for ${project.name}, cmd: ${command.joinToString(" ")}")
      return
    }

    val directory = if (workingDirectory != null) {
      VirtualFileManager.getInstance().findFileByNioPath(Path.of(workingDirectory))
    }
    else {
      project.guessProjectDir()
    }

    if (directory == null) {
      logger.warn("Cannot find working directory for ${project.name}")
      return
    }

    val sdk = runReadActionBlocking { ModuleUtilCore.findModuleForFile(directory, project)?.pythonSdk } ?: run {
      logger.warn("Cannot find Python SDK for directory ${directory} in project ${project.name}")
      return
    }

    val pythonEnvironment = sdk.pyRichSdk().environmentResult?.orLogException(logger) ?: run {
      logger.warn("Cannot detect Python environment for SDK ${sdk.homePath}, skipping activation")
      return
    }

    if (pythonEnvironment.pythonBinaryPath.getEelDescriptor() != terminalEelDescriptor) {
      logger.info("Skipping virtual env activation: interpreter ${pythonEnvironment.pythonBinaryPath} runs " +
                  "in a different environment than the terminal ($terminalEelDescriptor)")
      return
    }

    val jediterm = when (pythonEnvironment) {
      is PythonEnvironment.Venv -> {
        activateVenv(shell, sdk, pythonEnvironment, envs)
      }
      is PythonEnvironment.Conda -> {
        activateConda(shell, sdk, pythonEnvironment, envs)
      }
      is PythonEnvironment.SystemPython -> {
        logger.debug("No activation for system python ${sdk.homePath}")
        null
      }
    }
    jediterm?.buildEnvironmentVariables()?.let { envs.putAll(it) }


    logger.debug("Activating ${sdk.homePath} with ${envs.entries.joinToString("\n")}")
  }
}

/**
 * Content root of the **Python** module that owns [file], or `null` (to fall back to the platform default,
 * the project root) when [file] does not belong to the content root of a Python module.
 *
 * This makes a new terminal start in the module of the currently opened file, so that
 * [PyVirtualEnvTerminalCustomizer] activates that module's virtual environment instead of always
 * activating the root environment (PY-90039). Non-Python modules are skipped, so their content roots
 * never override the default terminal working directory.
 *
 * The module structure is read from the immutable [WorkspaceModel] snapshot, which is safe to access from
 * any thread without a read action. This matters because the function is called synchronously while run
 * configurations are loaded on startup, where blocking to acquire the read lock could deadlock (IDEA-390402).
 */
@ApiStatus.Internal
fun pyTerminalDefaultWorkingDirectory(project: Project, file: VirtualFile?): Path? {
  if (file == null || project.isDisposed) return null

  // Map every module content root to its owning module, read from the lock-free workspace-model snapshot.
  val contentRootToModule = WorkspaceModel.getInstance(project).currentSnapshot
    .entities<ModuleEntity>()
    .flatMap { module -> module.contentRoots.mapNotNull { it.url.virtualFile?.to(module) } }
    .toMap()

  // The innermost content root that contains the file, together with its owning module.
  val (contentRoot, module) = generateSequence(file) { it.parent }.firstNotNullOfOrNull { dir ->
    contentRootToModule[dir]?.let { dir to it }
  } ?: return null

  // Follow the module only if it's a Python module; otherwise keep the platform default.
  return if (module.type?.name == PyNames.PYTHON_MODULE_ID) contentRoot.toNioPathOrNull() else null
}
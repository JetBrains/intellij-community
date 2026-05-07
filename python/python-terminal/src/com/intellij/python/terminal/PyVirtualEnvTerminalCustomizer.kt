// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.terminal

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.Activatable
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.pyRichSdk
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.terminal.Shell
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
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
    put(JEDITERM_SOURCE_ARGS, sourceArgs?.joinToString(" ") ?: "")
    sourceArgs?.takeIf { it.isNotEmpty() }?.let {
      put(JEDITERM_SOURCE_SINGLE_ARG, "1")
    }
  }
}

class PyVirtualEnvTerminalCustomizer : LocalTerminalCustomizer() {
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

  override fun customizeCommandAndEnvironment(
    project: Project,
    workingDirectory: String?,
    command: Array<out String>,
    envs: MutableMap<String, String>,
  ): Array<out String> {
    if (!TerminalOptionsProvider.instance.shellIntegration) {
      logger.debug("Shell integration is disabled, skipping virtual env activation for ${project.name}")
      return command
    }

    if (!PyVirtualEnvTerminalSettings.getInstance(project).virtualEnvActivate) {
      logger.debug("Virtual env activation is disabled for ${project.name}")
      return command
    }

    val shell = Shell.resolve(command)
    if (shell == null) {
      logger.warn("No shell to run for ${project.name}, cmd: ${command.joinToString(" ")}")
      return command
    }

    val directory = if (workingDirectory != null) {
      VirtualFileManager.getInstance().findFileByNioPath(Path.of(workingDirectory))
    }
    else {
      project.guessProjectDir()
    }

    if (directory == null) {
      logger.warn("Cannot find working directory for ${project.name}")
      return command
    }


    val sdk = ModuleUtilCore.findModuleForFile(directory, project)?.pythonSdk ?: run {
      logger.warn("Cannot find Python SDK for directory ${directory} in project ${project.name}")
      return command
    }

    val pythonEnvironment = sdk.pyRichSdk().environmentResult?.orLogException(logger)

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
      null -> null
    }
    jediterm?.buildEnvironmentVariables()?.let { envs.putAll(it) }


    logger.debug("Running ${command.joinToString(" ")} with ${envs.entries.joinToString("\n")}")
    return command
  }

  @ApiStatus.Internal
  override fun getConfigurable(project: Project): UnnamedConfigurable = object : UiDslUnnamedConfigurable.Simple() {
    override fun Panel.createContent() {
      val settings = PyVirtualEnvTerminalSettings.getInstance(project)
      row {
        checkBox(PyTerminalBundle.message("activate.virtualenv.checkbox.text")).bindSelected(settings::virtualEnvActivate)
      }
    }
  }
}

@ApiStatus.Internal
internal class SettingsState {
  var virtualEnvActivate: Boolean = true
}

@Service(Service.Level.PROJECT)
@State(name = "PyVirtualEnvTerminalCustomizer", storages = [(Storage("python-terminal.xml"))])
@ApiStatus.Internal
internal class PyVirtualEnvTerminalSettings : PersistentStateComponent<SettingsState> {
  private var myState: SettingsState = SettingsState()

  var virtualEnvActivate: Boolean
    get() = myState.virtualEnvActivate
    set(value) {
      myState.virtualEnvActivate = value
    }

  override fun getState(): SettingsState = myState

  override fun loadState(state: SettingsState) {
    myState.virtualEnvActivate = state.virtualEnvActivate
  }

  companion object {
    fun getInstance(project: Project): PyVirtualEnvTerminalSettings {
      return project.getService(PyVirtualEnvTerminalSettings::class.java)
    }
  }
}

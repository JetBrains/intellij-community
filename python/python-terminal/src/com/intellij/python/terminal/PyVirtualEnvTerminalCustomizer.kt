// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.terminal

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.run.findActivateScript
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JCheckBox
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.name


class PyVirtualEnvTerminalCustomizer : LocalTerminalCustomizer() {
  private companion object {
    const val JEDITERM_SOURCE = "JEDITERM_SOURCE"
    const val JEDITERM_SOURCE_ARGS = "JEDITERM_SOURCE_ARGS"
    const val JEDITERM_SOURCE_SINGLE_ARG = "JEDITERM_SOURCE_SINGLE_ARG"
    val logger = fileLogger()
  }

  private fun generatePowerShellActivateScript(sdk: Sdk, sdkHomePath: VirtualFile): String? {
    // TODO: This should be migrated to Targets API: each target provides terminal
    val condaData = (sdk.sdkAdditionalData as? PythonSdkAdditionalData)?.flavorAndData?.data as? PyCondaFlavorData
    if (condaData != null) {
      // Activate conda
      val condaPath = Path(condaData.env.fullCondaPathOnTarget)
      return if (condaPath.exists() && condaPath.isExecutable()) {
        getCondaActivationCommand(condaPath, sdkHomePath)
      }
      else {
        logger<PyVirtualEnvTerminalCustomizer>().warn("Can't find $condaPath, will not activate conda")
        val message = PyTerminalBundle.message("powershell.conda.not.activated", "conda")
        "echo '$message'"
      }
    }

    // Activate convenient virtualenv
    val virtualEnvProfile = sdkHomePath.parent.findChild("activate.ps1") ?: return null
    return if (virtualEnvProfile.exists()) virtualEnvProfile.path else null
  }

  /**
   *``conda init`` installs conda activation hook into user profile
   *  We run this hook manually because we can't ask user to install hook and restart terminal
   *  In case of failure we ask user to run "conda init" manually
   */
  private fun getCondaActivationCommand(condaPath: Path, sdkHomePath: VirtualFile): String {
    // ' are in "Write-Host"
    val errorMessage = PyTerminalBundle.message("powershell.conda.not.activated", condaPath).replace('\'', '"')


    // No need to escape path: conda can't have spaces
    return """
        & '${StringUtil.escapeChar(condaPath.toString(), '\'')}' shell.powershell hook | Out-String | Invoke-Expression ; 
        try { 
          conda activate '${StringUtil.escapeChar(sdkHomePath.parent.path, '\'')}' 
        } catch { 
          Write-Host('${StringUtil.escapeChar(errorMessage, '\'')}') 
        }
        """.trimIndent()
  }

  override fun customizeCommandAndEnvironment(
    project: Project,
    workingDirectory: String?,
    command: Array<out String>,
    envs: MutableMap<String, String>,
  ): Array<out String> {
    var sdkByDirectory: Sdk? = null
    if (workingDirectory != null) {
      runReadAction {
        sdkByDirectory = PySdkUtil.findSdkForDirectory(project, Paths.get(workingDirectory), false)
      }
    }

    val sdk = sdkByDirectory
    if (sdk != null &&
        (PythonSdkUtil.isVirtualEnv(sdk) || PythonSdkUtil.isConda(sdk)) &&
        PyVirtualEnvTerminalSettings.getInstance(project).virtualEnvActivate) {
      // in case of virtualenv sdk on unix we activate virtualenv
      val sdkHomePath = sdk.homeDirectory

      if (sdkHomePath != null && command.isNotEmpty()) {
        val shellPath = command[0]
        if (isShellIntegrationAvailable(shellPath)) { //fish shell works only for virtualenv and not for conda
          //for bash we pass activate script to shell integration (see bash-integration.bash) to source it there
          //TODO: fix conda for fish [also in fleet.language.python.PythonVirtualEnvTerminalPreprocessor#preprocess]

          val shellName = Path(shellPath).name
          if (isPowerShell(shellName)) {
            generatePowerShellActivateScript(sdk, sdkHomePath)?.let {
              envs.put(JEDITERM_SOURCE, it)
            }
          }
          else {
            findActivateScript(sdkHomePath.path, shellPath)?.let { activate ->
              envs.put(JEDITERM_SOURCE, activate.first)
              envs.put(JEDITERM_SOURCE_ARGS, activate.second ?: "")
              // **nix shell integration scripts split arguments;
              // since a path may contain spaces, we do not want it to be split into several arguments.
              if (activate.second != null) {
                envs.put(JEDITERM_SOURCE_SINGLE_ARG, "1")
              }
            }
          }
        }
        else {
          //for other shells we read envs from activate script by the default shell and pass them to the process
          val envVars = PySdkUtil.activateVirtualEnv(sdk)
          if (envVars.isEmpty()) {
            Logger.getInstance(PyVirtualEnvTerminalCustomizer::class.java).warn("No vars found to activate in ${sdk.homePath}")
          }
          envs.putAll(envVars)
        }
      }
    }

    logger.debug("Running ${command.joinToString(" ")} with ${envs.entries.joinToString("\n")}")
    return command
  }

  private fun isShellIntegrationAvailable(shellPath: String): Boolean {
    if (TerminalOptionsProvider.instance.shellIntegration) {
      val shellName = File(shellPath).name
      return shellName == "bash"
             || (SystemInfo.isMac && shellName == "sh")
             || shellName == "zsh"
             || shellName == "fish"
             || isPowerShell(shellName)
    }
    return false
  }

  private fun isPowerShell(shellName: String): Boolean = shellName in arrayOf("powershell.exe", "pwsh.exe")

  @ApiStatus.Internal
  override fun getConfigurable(project: Project): UnnamedConfigurable = object : UnnamedConfigurable {
    val settings = PyVirtualEnvTerminalSettings.getInstance(project)

    var myCheckbox: JCheckBox = JCheckBox(PyTerminalBundle.message("activate.virtualenv.checkbox.text"))

    override fun createComponent() = myCheckbox

    override fun isModified() = myCheckbox.isSelected != settings.virtualEnvActivate

    override fun apply() {
      settings.virtualEnvActivate = myCheckbox.isSelected
    }

    override fun reset() {
      myCheckbox.isSelected = settings.virtualEnvActivate
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


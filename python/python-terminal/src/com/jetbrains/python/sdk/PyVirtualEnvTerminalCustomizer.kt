// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.python.run.findActivateScript
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import java.io.File
import javax.swing.JCheckBox

class PyVirtualEnvTerminalCustomizer : LocalTerminalCustomizer() {
  override fun customizeCommandAndEnvironment(project: Project,
                                              command: Array<out String>,
                                              envs: MutableMap<String, String>): Array<out String> {
    val sdk: Sdk? = findSdk(project)

    if (sdk != null &&
        (PythonSdkUtil.isVirtualEnv(sdk) || PythonSdkUtil.isConda(sdk)) &&
        PyVirtualEnvTerminalSettings.getInstance(project).virtualEnvActivate) {
      // in case of virtualenv sdk on unix we activate virtualenv
      val path = sdk.homePath

      if (path != null && command.isNotEmpty()) {
        val shellPath = command[0]
        if (isShellIntegrationAvailable(shellPath)) { //fish shell works only for virtualenv and not for conda
          //for bash we pass activate script to jediterm shell integration (see jediterm-bash.in) to source it there
          //TODO: fix conda for fish

          findActivateScript(path, shellPath)?.let { activate ->
            envs.put("JEDITERM_SOURCE",  activate.first)
            envs.put("JEDITERM_SOURCE_ARGS", activate.second?:"")
          }
        }
        else {
          //for other shells we read envs from activate script by the default shell and pass them to the process
          envs.putAll(PySdkUtil.activateVirtualEnv(sdk))
        }
      }
    }

    return command
  }

  private fun isShellIntegrationAvailable(shellPath: String) : Boolean {
    if (TerminalOptionsProvider.instance.shellIntegration) {
      val shellName = File(shellPath).name
      return shellName == "bash" || (SystemInfo.isMac && shellName == "sh") || shellName == "zsh" || shellName == "fish"
    }
    return false
  }

  private fun findSdk(project: Project): Sdk? {
    for (m in ModuleManager.getInstance(project).modules) {
      val sdk: Sdk? = PythonSdkUtil.findPythonSdk(m)
      if (sdk != null && !PythonSdkUtil.isRemote(sdk)) {
        return sdk
      }
    }

    return null
  }


  override fun getDefaultFolder(project: Project): String? {
    return null
  }

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

class SettingsState {
  var virtualEnvActivate: Boolean = true
}

@State(name = "PyVirtualEnvTerminalCustomizer", storages = [(Storage("python-terminal.xml"))])
class PyVirtualEnvTerminalSettings : PersistentStateComponent<SettingsState> {
  var myState: SettingsState = SettingsState()

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


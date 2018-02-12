// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import com.jetbrains.python.run.PyVirtualEnvReader
import com.jetbrains.python.run.findActivateScript
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer
import java.io.File
import javax.swing.JCheckBox

/**
 * @author traff
 */


class PyVirtualEnvTerminalCustomizer : LocalTerminalCustomizer() {
  override fun customizeCommandAndEnvironment(project: Project,
                                              command: Array<out String>,
                                              envs: MutableMap<String, String>): Array<out String> {
    val sdk: Sdk? = findSdk(project)

    if (sdk != null && (PythonSdkType.isVirtualEnv(sdk) || PythonSdkType.isCondaVirtualEnv(
      sdk)) && PyVirtualEnvTerminalSettings.getInstance(project).virtualEnvActivate) {
      // in case of virtualenv sdk on unix we activate virtualenv
      val path = sdk.homePath

      if (path != null) {

        val shellPath = command[0]
        val shellName = File(shellPath).name

        if (shellName == "bash" || (SystemInfo.isMac && shellName == "sh") || (shellName == "zsh") ||
            ((shellName == "fish") && PythonSdkType.isVirtualEnv(sdk))) { //fish shell works only for virtualenv and not for conda
          //for bash we pass activate script to jediterm shell integration (see jediterm-bash.in) to source it there
          findActivateScript(path, shellPath)?.let { activate ->
            val pathEnv = EnvironmentUtil.getEnvironmentMap().get("PATH")
            if (pathEnv != null) {
              envs.put("PATH", pathEnv)
            }
            envs.put("JEDITERM_SOURCE", if (activate.second != null) "${activate.first} ${activate.second}" else activate.first)
          }
        }
        else {
          //for other shells we read envs from activate script by the default shell and pass them to the process
          val reader = PyVirtualEnvReader(path)
          reader.activate?.let {
            // we add only envs that are setup by the activate script, because adding other variables from the different shell
            // can break the actual shell
            envs.putAll(reader.readPythonEnv().mapKeys { k -> k.key.toUpperCase() }.filterKeys { k ->
              k in PyVirtualEnvReader.virtualEnvVars
            })
          }
        }
      }
    }

    // for some reason virtualenv isn't activated in the rcfile for the login shell, so we make it non-login
    return command.filter { arg -> arg != "--login" && arg != "-l" }.toTypedArray()
  }


  private fun findSdk(project: Project): Sdk? {
    for (m in ModuleManager.getInstance(project).modules) {
      val sdk: Sdk? = PythonSdkType.findPythonSdk(m)
      if (sdk != null && !PythonSdkType.isRemote(sdk)) {
        return sdk
      }
    }

    return null
  }


  override fun getDefaultFolder(project: Project): String? {
    return null
  }

  override fun getConfigurable(project: Project) = object : UnnamedConfigurable {
    val settings = PyVirtualEnvTerminalSettings.getInstance(project)

    var myCheckbox: JCheckBox = JCheckBox("Activate virtualenv")

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
  var virtualEnvActivate = true
}

@State(name = "PyVirtualEnvTerminalCustomizer", storages = arrayOf(Storage("python-terminal.xml")))
class PyVirtualEnvTerminalSettings : PersistentStateComponent<SettingsState> {
  var myState = SettingsState()

  var virtualEnvActivate: Boolean
    get() = myState.virtualEnvActivate
    set(value) {
      myState.virtualEnvActivate = value
    }

  override fun getState() = myState

  override fun loadState(state: SettingsState) {
    myState.virtualEnvActivate = state.virtualEnvActivate
  }

  companion object {
    fun getInstance(project: Project): PyVirtualEnvTerminalSettings {
      return ServiceManager.getService(project, PyVirtualEnvTerminalSettings::class.java)
    }
  }

}


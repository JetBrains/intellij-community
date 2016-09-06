/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.sdk

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.python.run.PyVirtualEnvReader
import com.jetbrains.python.run.findActivateScript
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer
import java.io.File

/**
 * @author traff
 */

class PyVirtualEnvTerminalCustomizer : LocalTerminalCustomizer() {


  override fun customizeCommandAndEnvironment(project: Project,
                                              command: Array<out String>,
                                              envs: MutableMap<String, String>): Array<out String> {
    val sdk: Sdk? = findSdk(project)

    if (sdk != null && PythonSdkType.isVirtualEnv(sdk)) {
      // in case of virtualenv sdk on unix we activate virtualenv
      val path = sdk.homePath

      if (path != null) {

        val shellPath = command[0]
        val shellName = File(shellPath).name

        if (shellName == "bash" || shellName == "sh") {
          //for bash and sh we pass activate script to jediterm shell integration (see jediterm-sh.in) to source it there
          findActivateScript(path, shellPath)?.let { activate -> envs.put("JEDITERM_SOURCE", activate) }
        }
        else {
          //for other shells we read envs from activate script by the default shell and pass them to the process
          val reader = PyVirtualEnvReader(path)
          reader.activate?.let { envs.putAll(reader.readShellEnv()) }
        }

      }
    }

    // for some reason virtualenv isn't activated in the rcfile for the login shell, so we make it non-login
    return command.filter { arg -> arg != "--login" && arg != "-l"}.toTypedArray()
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


  override fun getDefaultFolder(): String? {
    return null
  }
}


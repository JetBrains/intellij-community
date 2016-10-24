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
package org.jetbrains.plugins.terminal

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.SystemInfo
import java.io.File
import kotlin.reflect.KProperty

/**
 * @author traff
 */
@State(name = "TerminalProjectOptionsProvider", storages = arrayOf(Storage("terminal.xml")))
class TerminalProjectOptionsProvider(private val myProject: Project) : PersistentStateComponent<TerminalProjectOptionsProvider.State> {

  private val myState = State()

  override fun getState(): State? {
    return myState
  }

  override fun loadState(state: State) {
    shellPath = state.myShellPath
    myState.myStartingDirectory = state.myStartingDirectory
  }

  class State {
    var myShellPath: String? = null
    var myStartingDirectory: String? = null
  }

  var shellPath: String? by ValueWithDefault { defaultShellPath }

  var startingDirectory: String? by ValueWithDefault { defaultStartingDirectory }

  val defaultStartingDirectory: String?
    get() {
      var directory: String? = null
      for (customizer in LocalTerminalCustomizer.EP_NAME.extensions) {
        try {

          if (directory == null) {
            directory = customizer.defaultFolder
          }
        }
        catch (e: Exception) {
          LOG.error("Exception during getting default folder", e)
        }
      }

      return currentProjectFolder()
    }


  private fun currentProjectFolder(): String? {
    val projectRootManager = ProjectRootManager.getInstance(myProject)

    val roots = projectRootManager.contentRoots
    if (roots.size == 1) {
      roots[0].canonicalPath
    }
    val baseDir = myProject.baseDir
    return baseDir?.canonicalPath
  }

  val defaultShellPath: String
    get() {
      val shell = System.getenv("SHELL")

      if (shell != null && File(shell).canExecute()) {
        return shell
      }

      if (SystemInfo.isUnix) {
        if (File("/bin/bash").exists()) {
          return "/bin/bash"
        }
        else {
          return "/bin/sh"
        }
      }
      else {
        return "cmd.exe"
      }
    }

  companion object {
    private val LOG = Logger.getInstance(TerminalProjectOptionsProvider::class.java)


    fun getInstance(project: Project): TerminalProjectOptionsProvider {
      return ServiceManager.getService(project, TerminalProjectOptionsProvider::class.java)
    }
  }

}

class ValueWithDefault(val default: () -> String?) {
  private var _value: String? = null

  operator fun getValue(thisRef: Any?, property: KProperty<*>): String? {
    return if (_value !== null) _value else default()
  }

  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
    _value = if (value == default() || value.isNullOrEmpty()) null else value
  }
}




// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty

/**
 * @author traff
 */
@State(name = "TerminalProjectOptionsProvider", storages = [(Storage("terminal.xml"))])
class TerminalProjectOptionsProvider(val project: Project) : PersistentStateComponent<TerminalProjectOptionsProvider.State> {

  private val myState = State()

  override fun getState(): State? {
    return myState
  }

  override fun loadState(state: State) {
    myState.myStartingDirectory = state.myStartingDirectory
  }

  class State {
    var myStartingDirectory: String? = null
  }

  var startingDirectory: String? by ValueWithDefault(State::myStartingDirectory, myState) { defaultStartingDirectory }

  val defaultStartingDirectory: String?
    get() {
      var directory: String? = null
      for (customizer in LocalTerminalCustomizer.EP_NAME.extensions) {
        try {

          if (directory == null) {
            directory = customizer.getDefaultFolder(project)
          }
        }
        catch (e: Exception) {
          LOG.error("Exception during getting default folder", e)
        }
      }

      return directory ?: currentProjectFolder()
    }


  private fun currentProjectFolder(): String? {
    val projectRootManager = ProjectRootManager.getInstance(project)

    val roots = projectRootManager.contentRoots
    if (roots.size == 1) {
      roots[0].canonicalPath
    }
    val baseDir = project.baseDir
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

// TODO: In Kotlin 1.1 it will be possible to pass references to instance properties. Until then we need 'state' argument as a reciever for
// to property to apply
class ValueWithDefault<S>(val prop: KMutableProperty1<S, String?>, val state: S, val default: () -> String?) {
  operator fun getValue(thisRef: Any?, property: KProperty<*>): String? {
    return if (prop.get(state) !== null) prop.get(state) else default()
  }

  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
    prop.set(state, if (value == default() || value.isNullOrEmpty()) null else value)
  }
}




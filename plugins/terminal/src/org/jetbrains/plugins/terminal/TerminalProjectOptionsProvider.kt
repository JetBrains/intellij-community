// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.xmlb.annotations.Property
import java.io.File
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

@State(name = "TerminalProjectNonSharedOptionsProvider", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))])
class TerminalProjectOptionsProvider(val project: Project) : PersistentStateComponent<TerminalProjectOptionsProvider.State> {

  private val state = State()

  override fun getState(): State? {
    return state
  }

  override fun loadState(newState: State) {
    state.startingDirectory = newState.startingDirectory
    state.shellPath = newState.shellPath
    state.envDataOptions = newState.envDataOptions
  }

  fun getEnvData(): EnvironmentVariablesData {
    return state.envDataOptions.get()
  }

  fun setEnvData(envData: EnvironmentVariablesData) {
    state.envDataOptions.set(envData)
  }

  class State {
    var startingDirectory: String? = null
    var shellPath: String? = null
    @get:Property(surroundWithTag = false, flat = true)
    var envDataOptions = EnvironmentVariablesDataOptions()
  }

  var startingDirectory: String? by ValueWithDefault(state::startingDirectory) { defaultStartingDirectory }

  val defaultStartingDirectory: String?
    get() {
      var directory: String? = null
      for (customizer in LocalTerminalCustomizer.EP_NAME.extensions) {
        try {
          directory = customizer.getDefaultFolder(project)
          if (directory != null) {
            break
          }
        }
        catch (e: Exception) {
          LOG.error("Exception during getting default folder", e)
        }
      }
      if (directory == null) {
        directory = getDefaultWorkingDirectory()
      }
      return if (directory != null) FileUtil.toSystemDependentName(directory) else null
    }

  private fun getDefaultWorkingDirectory(): String? {
    val roots = ProjectRootManager.getInstance(project).contentRoots
    @Suppress("DEPRECATION")
    val dir = if (roots.size == 1 && roots[0] != null && roots[0].isDirectory) roots[0] else project.baseDir
    return dir?.canonicalPath
  }

  var shellPath: String by ValueWithDefault(state::shellPath) { defaultShellPath() }

  fun defaultShellPath(): String {
    if (SystemInfo.isWindows) {
      val wslDistribution = findWslDistribution(startingDirectory)
      if (wslDistribution != null) {
        return "wsl.exe --distribution $wslDistribution"
      }
    }
    val shell = System.getenv("SHELL")
    if (shell != null && File(shell).canExecute()) {
      return shell
    }
    if (SystemInfo.isUnix) {
      val bashPath = "/bin/bash"
      if (File(bashPath).exists()) {
        return bashPath
      }
      return "/bin/sh"
    }
    return "cmd.exe"
  }

  private fun findWslDistribution(directory: String?): String? {
    if (directory == null) return null
    val prefix = "\\\\wsl$\\"
    if (!directory.startsWith(prefix)) return null
    val endInd = directory.indexOf('\\', prefix.length)
    return if (endInd >= 0) directory.substring(prefix.length, endInd) else null
  }

  companion object {
    private val LOG = Logger.getInstance(TerminalProjectOptionsProvider::class.java)

    @JvmStatic
    fun getInstance(project: Project): TerminalProjectOptionsProvider {
      val provider = project.getService(TerminalProjectOptionsProvider::class.java)
      val oldState = project.getService(TerminalProjectOptionsProviderOld::class.java).getAndClear()
      if (oldState != null &&
          provider.state.startingDirectory == null &&
          provider.state.shellPath == null &&
          provider.state.envDataOptions.get() == EnvironmentVariablesData.DEFAULT) {
        provider.state.startingDirectory = oldState.myStartingDirectory
        provider.state.shellPath = oldState.myShellPath
        provider.state.envDataOptions.set(oldState.envDataOptions.get())
      }
      return provider
    }
  }

}

class ValueWithDefault<T : String?>(val prop: KMutableProperty0<T?>, val default: () -> T) {
  operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
    val value : T? = prop.get()
    return if (value !== null) value else default()
  }

  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    prop.set(if (value == default() || value.isNullOrEmpty()) null else value)
  }
}

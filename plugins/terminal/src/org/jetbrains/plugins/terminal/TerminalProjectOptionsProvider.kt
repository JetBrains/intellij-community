// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.xmlb.annotations.Property
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
    myState.envDataOptions = state.envDataOptions
  }

  fun getEnvData(): EnvironmentVariablesData {
    return myState.envDataOptions.get()
  }

  fun setEnvData(envData: EnvironmentVariablesData) {
    myState.envDataOptions.set(envData)
  }

  class State {
    var myStartingDirectory: String? = null
    @get:Property(surroundWithTag = false, flat = true)
    var envDataOptions = EnvironmentVariablesDataOptions()
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

      return directory ?: getDefaultWorkingDirectory()
    }

  private fun getDefaultWorkingDirectory(): String? {
    val roots = ProjectRootManager.getInstance(project).contentRoots
    @Suppress("DEPRECATION")
    val dir = if (roots.size == 1 && roots[0] != null && roots[0].isDirectory) roots[0] else project.baseDir
    return dir?.canonicalPath
  }

  companion object {
    private val LOG = Logger.getInstance(TerminalProjectOptionsProvider::class.java)

    @JvmStatic
    fun getInstance(project: Project): TerminalProjectOptionsProvider {
      val provider = ServiceManager.getService(project, TerminalProjectOptionsProvider::class.java)
      val appEnvData = TerminalOptionsProvider.instance.getEnvData()
      if (provider.getEnvData() == EnvironmentVariablesData.DEFAULT && appEnvData != EnvironmentVariablesData.DEFAULT) {
        provider.setEnvData(appEnvData)
        TerminalOptionsProvider.instance.setEnvData(EnvironmentVariablesData.DEFAULT)
      }
      return provider
    }
  }

}

// TODO: In Kotlin 1.1 it will be possible to pass references to instance properties. Until then we need 'state' argument as a receiver for
// to property to apply
class ValueWithDefault<S>(val prop: KMutableProperty1<S, String?>, val state: S, val default: () -> String?) {
  operator fun getValue(thisRef: Any?, property: KProperty<*>): String? {
    return if (prop.get(state) !== null) prop.get(state) else default()
  }

  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
    prop.set(state, if (value == default() || value.isNullOrEmpty()) null else value)
  }
}

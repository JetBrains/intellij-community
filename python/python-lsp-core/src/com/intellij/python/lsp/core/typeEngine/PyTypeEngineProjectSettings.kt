package com.intellij.python.lsp.core.typeEngine

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.python.lsp.core.listener.PyLspListener
import com.intellij.util.xmlb.XmlSerializerUtil


@Service(Service.Level.PROJECT)
@State(name = "PyTypeEngineProjectSettings", storages = [Storage("pyTypeEngine.xml")])
class PyTypeEngineProjectSettings(val project: Project) : PersistentStateComponent<PyTypeEngineSettingsState> {
  private var state: PyTypeEngineSettingsState = PyTypeEngineSettingsState()

  /** The selected engine, or [defaultTypeEngine] when the project has not selected one. */
  var typeEngine: PyTypeEngineType
    get() = state.typeEngine ?: defaultTypeEngine()
    set(value) {
      if (value == typeEngine) return
      state.typeEngine = value
      project.messageBus.syncPublisher(PyLspListener.TOPIC).onTypeSettingsChange()
    }

  override fun getState(): PyTypeEngineSettingsState = state

  override fun loadState(state: PyTypeEngineSettingsState): Unit = XmlSerializerUtil.copyBean(state, this.state)


  companion object {
    fun getInstance(project: Project): PyTypeEngineProjectSettings = project.service<PyTypeEngineProjectSettings>()

    /**
     * The engine of a project that has not selected one.
     *
     * The bundled Pyrefly is the default, see the `use.bundled.pyrefly` key of the Pyrefly plugin. The key
     * is absent when the plugin is, so the built-in engine answers there. A test selects Pyrefly
     * explicitly: the dev-mode test runner bundles the executable, and the default must not switch every
     * Python test to the Pyrefly server.
     */
    fun defaultTypeEngine(): PyTypeEngineType {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return PyTypeEngineType.PYCHARM
      }
      return if (Registry.`is`("use.bundled.pyrefly", false)) PyTypeEngineType.PYREFLY else PyTypeEngineType.PYCHARM
    }
  }
}


data class PyTypeEngineSettingsState(
  /** `null` until the project selects an engine, so the default can change between releases. */
  var typeEngine: PyTypeEngineType? = null,
)

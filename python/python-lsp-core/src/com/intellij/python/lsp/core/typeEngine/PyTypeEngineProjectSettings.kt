package com.intellij.python.lsp.core.typeEngine

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.lsp.core.listener.PyLspListener
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.ApiStatus


@Service(Service.Level.PROJECT)
@State(name = "PyTypeEngineProjectSettings", storages = [Storage("pyTypeEngine.xml")])
@ApiStatus.Internal
class PyTypeEngineProjectSettings(val project: Project) : PersistentStateComponent<PyTypeEngineSettingsState> {
  private var state: PyTypeEngineSettingsState = PyTypeEngineSettingsState()

  var typeEngine: PyTypeEngineType
    get() = state.typeEngine
    set(value) {
      if (value == state.typeEngine) return
      state.typeEngine = value
      project.messageBus.syncPublisher(PyLspListener.TOPIC).onTypeSettingsChange()
    }

  override fun getState(): PyTypeEngineSettingsState = state

  override fun loadState(state: PyTypeEngineSettingsState): Unit = XmlSerializerUtil.copyBean(state, this.state)


  companion object {
    fun getInstance(project: Project): PyTypeEngineProjectSettings = project.service<PyTypeEngineProjectSettings>()
  }
}


@ApiStatus.Internal
data class PyTypeEngineSettingsState(
  var typeEngine: PyTypeEngineType = PyTypeEngineType.PYCHARM,
)

package com.intellij.python.terminal.shared

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
@State(name = PyVirtualEnvTerminalSettings.COMPONENT_NAME, storages = [(Storage("python-terminal.xml"))])
class PyVirtualEnvTerminalSettings : PersistentStateComponent<PyVirtualEnvTerminalSettings.SettingsState> {
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

  override fun noStateLoaded() {
    // Required for the RemDev case: when remote settings are changed to the defaults, platform calls `noStateLoaded` instead of `loadState`.
    loadState(SettingsState())
  }

  class SettingsState {
    var virtualEnvActivate: Boolean = true
  }

  companion object {
    fun getInstance(project: Project): PyVirtualEnvTerminalSettings {
      return project.getService(PyVirtualEnvTerminalSettings::class.java)
    }

    const val COMPONENT_NAME: String = "PyVirtualEnvTerminalCustomizer"
  }
}

internal class PyVirtualEnvTerminalRemoteSettingInfoProvider : RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> {
    return mapOf(PyVirtualEnvTerminalSettings.COMPONENT_NAME to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromBackend))
  }
}
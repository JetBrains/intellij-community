package com.intellij.settingsSync

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name="SettingsSyncSettings", storages=[Storage("settingsSync.xml")])
internal class SettingsSyncSettings : SimplePersistentStateComponent<SettingsSyncSettings.SettingsSyncSettingsState>(SettingsSyncSettingsState()) {

  var syncEnabled
    get() = state.syncEnabled
    set(value) {
      state.syncEnabled = value
    }

  class SettingsSyncSettingsState : BaseState() {
    var syncEnabled by property(false)
  }
}
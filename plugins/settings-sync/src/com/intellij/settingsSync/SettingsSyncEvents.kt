package com.intellij.settingsSync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.EventDispatcher

internal class SettingsSyncEvents {

  private val settingsChangeDispatcher = EventDispatcher.create(SettingsChangeListener::class.java)

  fun addSettingsChangedListener(settingsChangeListener: SettingsChangeListener) {
    settingsChangeDispatcher.addListener(settingsChangeListener)
  }

  fun fireSettingsChanged(event: SyncSettingsEvent) {
    settingsChangeDispatcher.multicaster.settingChanged(event)
  }

  companion object {
    fun getInstance(): SettingsSyncEvents = ApplicationManager.getApplication().getService(SettingsSyncEvents::class.java)
  }
}
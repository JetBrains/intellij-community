package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class SettingsSyncEvents : Disposable {

  private val eventDispatcher = EventDispatcher.create(SettingsSyncEventListener::class.java)

  fun addListener(listener: SettingsSyncEventListener) {
    eventDispatcher.addListener(listener)
  }

  fun addListener(listener: SettingsSyncEventListener, parentDisposable: Disposable? = null) {
    eventDispatcher.addListener(listener, parentDisposable ?: this)
  }

  fun removeListener(listener: SettingsSyncEventListener) {
    eventDispatcher.removeListener(listener)
  }

  fun fireSettingsChanged(event: SyncSettingsEvent) {
    eventDispatcher.multicaster.settingChanged(event)
  }

  fun fireCategoriesChanged() {
    eventDispatcher.multicaster.categoriesStateChanged()
  }

  fun fireEnabledStateChanged(syncEnabled: Boolean) {
    eventDispatcher.multicaster.enabledStateChanged(syncEnabled)
  }

  fun fireRestartRequired(cause: String, details: String) {
    eventDispatcher.multicaster.restartRequired(cause, details)
  }

  companion object {
    fun getInstance(): SettingsSyncEvents = ApplicationManager.getApplication().getService(SettingsSyncEvents::class.java)
  }

  override fun dispose() {
    eventDispatcher.listeners.clear()
  }
}

interface SettingsSyncEventListener : EventListener {
  fun categoriesStateChanged() {}
  fun settingChanged(event: SyncSettingsEvent) {}
  fun enabledStateChanged(syncEnabled: Boolean) {}
  fun restartRequired(cause: String, details: String) {}
}
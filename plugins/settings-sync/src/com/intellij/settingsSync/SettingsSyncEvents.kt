package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.EventDispatcher
import java.util.*

internal class SettingsSyncEvents : Disposable {

  private val settingsChangeDispatcher = EventDispatcher.create(SettingsChangeListener::class.java)
  private val enabledStateChangeDispatcher = EventDispatcher.create(SettingsSyncEnabledStateListener::class.java)
  private val categoriesChangeDispatcher = EventDispatcher.create(SettingsSyncCategoriesChangeListener::class.java)

  fun addSettingsChangedListener(settingsChangeListener: SettingsChangeListener) {
    settingsChangeDispatcher.addListener(settingsChangeListener)
  }

  fun removeSettingsChangedListener(settingsChangeListener: SettingsChangeListener) {
    settingsChangeDispatcher.removeListener(settingsChangeListener)
  }

  fun fireSettingsChanged(event: SyncSettingsEvent) {
    settingsChangeDispatcher.multicaster.settingChanged(event)
  }

  fun addCategoriesChangeListener(categoriesChangeListener: SettingsSyncCategoriesChangeListener) {
    categoriesChangeDispatcher.addListener(categoriesChangeListener)
  }

  fun removeCategoriesChangeListener(categoriesChangeListener: SettingsSyncCategoriesChangeListener) {
    categoriesChangeDispatcher.removeListener(categoriesChangeListener)
  }

  fun fireCategoriesChanged() {
    categoriesChangeDispatcher.multicaster.categoriesStateChanged()
  }

  fun addEnabledStateChangeListener(listener: SettingsSyncEnabledStateListener, parentDisposable: Disposable? = null) {
    if (parentDisposable != null) enabledStateChangeDispatcher.addListener(listener, parentDisposable)
    else enabledStateChangeDispatcher.addListener(listener, this)
  }

  fun fireEnabledStateChanged(syncEnabled: Boolean) {
    enabledStateChangeDispatcher.multicaster.enabledStateChanged(syncEnabled)
  }

  companion object {
    fun getInstance(): SettingsSyncEvents = ApplicationManager.getApplication().getService(SettingsSyncEvents::class.java)
  }

  override fun dispose() {
    settingsChangeDispatcher.listeners.clear()
    enabledStateChangeDispatcher.listeners.clear()
  }
}

interface SettingsSyncCategoriesChangeListener : EventListener {
  fun categoriesStateChanged()
}
package com.intellij.settingsSync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.Nls
import java.util.*

@Service
internal final class SettingsSyncStatusTracker : SettingsSyncEnabledStateListener {
  private var lastSyncTime = -1L
  private var errorMessage = ""

  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  init {
    SettingsSyncEvents.getInstance().addEnabledStateChangeListener(this)
  }

  companion object {
    fun getInstance(): SettingsSyncStatusTracker = ApplicationManager.getApplication().getService(SettingsSyncStatusTracker::class.java)
  }

  fun updateOnSuccess() {
    lastSyncTime = System.currentTimeMillis()
    errorMessage = ""
    eventDispatcher.multicaster.syncStatusChanged()
  }

  fun updateOnError(message: @Nls String) {
    lastSyncTime = -1
    errorMessage = message
    eventDispatcher.multicaster.syncStatusChanged()
  }

  fun isSyncSuccessful() = lastSyncTime >= 0

  fun getLastSyncTime() = lastSyncTime

  private fun clear() {
    lastSyncTime = -1
    errorMessage = ""
  }

  override fun enabledStateChanged(syncEnabled: Boolean) {
    if (!syncEnabled) clear()
  }

  fun addListener(listener: Listener) {
    eventDispatcher.addListener(listener)
  }

  fun removeListener(listener: Listener) {
    eventDispatcher.removeListener(listener)
  }

  interface Listener : EventListener {
    fun syncStatusChanged()
  }
}
package com.intellij.settingsSync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.Nls
import java.util.*

@Service
class SettingsSyncStatusTracker {
  private var lastSyncTime = -1L
  private var errorMessage: String? = null

  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  init {
    SettingsSyncEvents.getInstance().addListener(object: SettingsSyncEventListener{
      override fun settingChanged(event: SyncSettingsEvent) {
        if (event is SyncSettingsEvent.CloudChange) {
          updateOnSuccess()
        }
      }

      override fun enabledStateChanged(syncEnabled: Boolean) {
        logger<SettingsSyncStatusTracker>().info("Settings sync enabled state changed to: $syncEnabled")
        if (!syncEnabled) clear()
      }
    })
  }

  companion object {
    fun getInstance(): SettingsSyncStatusTracker = ApplicationManager.getApplication().getService(SettingsSyncStatusTracker::class.java)
  }

  fun updateOnSuccess() {
    lastSyncTime = System.currentTimeMillis()
    errorMessage = null
    eventDispatcher.multicaster.syncStatusChanged()
  }

  fun updateOnError(message: @Nls String) {
    lastSyncTime = -1
    errorMessage = message
    eventDispatcher.multicaster.syncStatusChanged()
  }

  fun isSyncSuccessful() = errorMessage == null

  fun isSynced() = lastSyncTime >= 0

  fun getLastSyncTime() = lastSyncTime

  private fun clear() {
    lastSyncTime = -1
    errorMessage = null
  }

  fun getErrorMessage(): String? = errorMessage

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
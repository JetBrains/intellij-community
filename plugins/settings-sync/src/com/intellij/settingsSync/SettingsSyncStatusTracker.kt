package com.intellij.settingsSync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import org.jetbrains.annotations.Nls

@Service
internal final class SettingsSyncStatusTracker : SettingsSyncEnabledStateListener {
  private var lastSyncTime = -1L
  private var errorMessage = ""

  init {
    SettingsSyncEvents.getInstance().addEnabledStateChangeListener(this)
  }

  companion object {
    fun getInstance(): SettingsSyncStatusTracker = ApplicationManager.getApplication().getService(SettingsSyncStatusTracker::class.java)
  }

  fun updateStatus(result: UpdateResult) {
    if (result is UpdateResult.Success) {
      updateOnSuccess()
    }
    else if (result == UpdateResult.NoFileOnServer) {
      updateOnError(SettingsSyncBundle.message("status.tracker.no.file.on.server"))
    }
    else if (result is UpdateResult.Error) {
      errorMessage = result.message
    }
  }

  fun updateStatus(result: SettingsSyncPushResult) {
    if (result == SettingsSyncPushResult.Success) {
      updateOnSuccess()
    }
    else if (result == SettingsSyncPushResult.Rejected) {
      updateOnError(SettingsSyncBundle.message("status.tracker.push.rejected"))
    }
    if (result is SettingsSyncPushResult.Error) {
      updateOnError(result.message)
    }
  }

  private fun updateOnSuccess() {
    lastSyncTime = System.currentTimeMillis()
    errorMessage = ""
  }

  private fun updateOnError(message: @Nls String) {
    lastSyncTime = -1
    errorMessage = message
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
}
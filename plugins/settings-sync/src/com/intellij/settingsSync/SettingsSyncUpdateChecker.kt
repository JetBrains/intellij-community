package com.intellij.settingsSync

import com.intellij.openapi.application.Application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

internal class SettingsSyncUpdateChecker(private val application: Application,
                                         private val remoteCommunicator: SettingsSyncRemoteCommunicator) {

  @RequiresBackgroundThread
  fun scheduleUpdateFromServer() : UpdateResult {
    val updateResult = remoteCommunicator.receiveUpdates()
    SettingsSyncStatusTracker.getInstance().updateStatus(updateResult)
    if (updateResult is UpdateResult.Success) {
      val snapshot = updateResult.settingsSnapshot
      SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.CloudChange(snapshot))
    }
    return updateResult
  }

}

package com.intellij.settingsSync

import com.intellij.openapi.application.Application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

internal class SettingsSyncUpdateChecker(private val application: Application,
                                         private val remoteCommunicator: SettingsSyncRemoteCommunicator) {

  @RequiresBackgroundThread
  fun scheduleUpdateFromServer() : UpdateResult {
    val updateResult = remoteCommunicator.receiveUpdates()
    if (updateResult is UpdateResult.Success) {
      val snapshot = updateResult.settingsSnapshot
      application.messageBus.syncPublisher(SETTINGS_CHANGED_TOPIC).settingChanged(SyncSettingsEvent.CloudChange(snapshot))
    }
    return updateResult
  }

  fun isUpdateNeeded(): Boolean {
    return remoteCommunicator.isUpdateNeeded()
  }
}

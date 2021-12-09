package com.intellij.settingsSync

import com.intellij.openapi.application.Application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

internal class SettingsSyncUpdateChecker(private val application: Application,
                                         private val remoteCommunicator: SettingsSyncRemoteCommunicator) {

  @RequiresBackgroundThread
  fun updateFromServer() {
    val updateResult = remoteCommunicator.receiveUpdates()
    when (updateResult) {
      is UpdateResult.Success -> {
        val snapshot = updateResult.settingsSnapshot
        val event = SettingsChangeEvent(ChangeSource.FROM_SERVER, snapshot)
        application.messageBus.syncPublisher(SETTINGS_CHANGED_TOPIC).settingChanged(event)
      }
      is UpdateResult.Error -> {
        // todo remove the error notification after next successful update
        notifyError(SettingsSyncBundle.message("notification.title.update.error"), updateResult.message)
        return
      }
      is UpdateResult.NoFileOnServer -> {
        notifyError(SettingsSyncBundle.message("notification.title.update.error"),
                    SettingsSyncBundle.message("notification.title.update.no.such.file"))
      }
    }
  }

  // todo update by app focus receive & by timer
}

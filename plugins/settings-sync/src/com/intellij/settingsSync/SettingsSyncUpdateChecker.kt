package com.intellij.settingsSync

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SettingsSyncUpdateChecker(private val remoteCommunicator: SettingsSyncRemoteCommunicator) {

  companion object {
    private val LOG = logger<SettingsSyncUpdateChecker>()
  }

  @RequiresBackgroundThread
  fun scheduleUpdateFromServer() : UpdateResult {
    val updateResult = remoteCommunicator.receiveUpdates()
    when(updateResult) {
      is UpdateResult.Success -> {
        val snapshot = updateResult.settingsSnapshot
        SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.CloudChange(snapshot, updateResult.serverVersionId))
      }
      is UpdateResult.FileDeletedFromServer -> {
        SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.DeletedOnCloud)
      }
      is UpdateResult.NoFileOnServer -> {
        LOG.info("Settings update requested, but there was no file on the server.")
      }
      is UpdateResult.Error -> {
        LOG.warn("Settings update requested, but failed with error: " + updateResult.message)
        SettingsSyncStatusTracker.getInstance().updateOnError(
          SettingsSyncBundle.message("notification.title.update.error") + ": " + updateResult.message)

      }
    }
    return updateResult
  }

}

package com.intellij.settingsSync

import com.intellij.openapi.diagnostic.logger

internal class SettingsSyncPusher(private val settingsLog: GitSettingsLog,
                                  private val remoteCommunicator: SettingsSyncRemoteCommunicator) {

  // todo notify error only when called explicitly, otherwise just set the status
  internal fun push() {
    val pushResult = remoteCommunicator.push(settingsLog.getCurrentSnapshot())
    when (pushResult) {
      is SettingsSyncPushResult.Success -> {
        settingsLog.pushedSuccessfully()
      }
      is SettingsSyncPushResult.Rejected -> {
        val updateResult = remoteCommunicator.receiveUpdates()
        when (updateResult) {
          is UpdateResult.Success -> {
            settingsLog.pull(updateResult.settingsSnapshot) // todo handle conflicts and another merge
            push() // todo push only if pull succeeded
          }
          is UpdateResult.Error -> {
            notifyError(SettingsSyncBundle.message("notification.title.push.error"), updateResult.message)
            return
          }
          is UpdateResult.NoFileOnServer -> {
            LOG.error("No settings file on the server, but push has been rejected")
            notifyError(SettingsSyncBundle.message("notification.title.update.error"),
                        SettingsSyncBundle.message("notification.title.update.no.such.file"))
            return
          }
        }
      }
      is SettingsSyncPushResult.Error -> {
        notifyError(SettingsSyncBundle.message("notification.title.push.error"), pushResult.message)
      }
    }
  }

  companion object {
    val LOG = logger<SettingsSyncPusher>()
  }
}
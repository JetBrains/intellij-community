package com.intellij.settingsSync

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
          is UpdateResult.Error -> {
            notifyError(SettingsSyncBundle.message("notification.title.push.error"), updateResult.message)
            return
          }
          is UpdateResult.Success -> {
            settingsLog.pull(updateResult.settingsSnapshot) // todo handle conflicts and another merge
            push() // todo push only if pull succeeded
          }
        }
      }
      is SettingsSyncPushResult.Error -> {
        notifyError(SettingsSyncBundle.message("notification.title.push.error"), pushResult.message)
      }
    }
  }
}
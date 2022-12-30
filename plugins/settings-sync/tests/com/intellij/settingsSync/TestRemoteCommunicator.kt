package com.intellij.settingsSync

internal abstract class TestRemoteCommunicator : SettingsSyncRemoteCommunicator {

  abstract fun prepareFileOnServer(snapshot: SettingsSnapshot)

  abstract fun getVersionOnServer(): SettingsSnapshot?

  abstract fun awaitForPush(): SettingsSnapshot?

}
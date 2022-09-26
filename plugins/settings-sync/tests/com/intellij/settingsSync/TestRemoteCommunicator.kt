package com.intellij.settingsSync

internal abstract class TestRemoteCommunicator : SettingsSyncRemoteCommunicator {

  var versionOnServer: SettingsSnapshot? = null
    protected set

  abstract fun prepareFileOnServer(snapshot: SettingsSnapshot)

  abstract fun awaitForPush(): SettingsSnapshot?

}
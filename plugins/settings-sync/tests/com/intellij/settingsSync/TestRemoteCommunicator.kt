package com.intellij.settingsSync

internal abstract class TestRemoteCommunicator : SettingsSyncRemoteCommunicator {

  var latestPushedSnapshot: SettingsSnapshot? = null
    protected set

  abstract fun prepareFileOnServer(snapshot: SettingsSnapshot)

  abstract fun awaitForPush(): SettingsSnapshot?

}
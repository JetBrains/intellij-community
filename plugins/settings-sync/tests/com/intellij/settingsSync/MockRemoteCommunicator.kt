package com.intellij.settingsSync

import java.util.concurrent.CountDownLatch

internal class MockRemoteCommunicator : SettingsSyncRemoteCommunicator {
  var offline: Boolean = false
  var newVersionOnServer: UpdateResult? = null
  var pushed: SettingsSnapshot? = null
  var startPushLatch: CountDownLatch? = null
  lateinit var pushedLatch: CountDownLatch

  override fun checkServerState(): ServerState {
    return if (newVersionOnServer != null) ServerState.UpdateNeeded else ServerState.UpToDate
  }

  override fun receiveUpdates(): UpdateResult {
    val result = newVersionOnServer
    newVersionOnServer = null
    return result ?: UpdateResult.Error("Unexpectedly null update result")
  }

  override fun push(snapshot: SettingsSnapshot): SettingsSyncPushResult {
    startPushLatch?.countDown()
    if (offline) return SettingsSyncPushResult.Error("Offline")

    pushed = snapshot
    if (::pushedLatch.isInitialized) pushedLatch.countDown()
    return SettingsSyncPushResult.Success
  }

  override fun delete() {}
}
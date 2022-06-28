package com.intellij.settingsSync

import java.util.concurrent.CountDownLatch

internal class TestRemoteCommunicator : SettingsSyncRemoteCommunicator {
  var offline: Boolean = false
  var updateResult: UpdateResult? = null
  var pushed: SettingsSnapshot? = null
  var startPushLatch: CountDownLatch? = null
  lateinit var pushedLatch: CountDownLatch

  override fun checkServerState(): ServerState {
    return ServerState.UpdateNeeded
  }

  override fun receiveUpdates(): UpdateResult {
    return updateResult ?: UpdateResult.Error("Unexpectedly null update result")
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
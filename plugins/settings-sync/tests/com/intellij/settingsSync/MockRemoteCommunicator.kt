package com.intellij.settingsSync

import org.junit.Assert
import java.util.concurrent.CountDownLatch

internal class MockRemoteCommunicator : TestRemoteCommunicator() {
  private var versionOnServer: SettingsSnapshot? = null
  private var downloadedLatestVersion = false

  private lateinit var pushedLatch: CountDownLatch

  override fun prepareFileOnServer(snapshot: SettingsSnapshot) {
    versionOnServer = snapshot
  }

  override fun checkServerState(): ServerState {
    return if (versionOnServer == null) ServerState.FileNotExists
    else if (downloadedLatestVersion) ServerState.UpToDate
    else ServerState.UpdateNeeded
  }

  override fun receiveUpdates(): UpdateResult {
    downloadedLatestVersion = true
    return versionOnServer?.let { UpdateResult.Success(it, null) } ?: UpdateResult.Error("Unexpectedly null update result")
  }

  override fun awaitForPush(): SettingsSnapshot? {
    pushedLatch = CountDownLatch(1)
    Assert.assertTrue("Didn't await until changes are pushed", pushedLatch.await(5, TIMEOUT_UNIT))
    return latestPushedSnapshot
  }

  override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
    latestPushedSnapshot = snapshot
    if (::pushedLatch.isInitialized) pushedLatch.countDown()
    return SettingsSyncPushResult.Success(null)
  }

  override fun delete() {}
}
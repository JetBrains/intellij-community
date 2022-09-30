package com.intellij.settingsSync

import com.intellij.openapi.util.io.FileUtil
import org.junit.Assert
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.CountDownLatch

internal class MockRemoteCommunicator : TestRemoteCommunicator() {
  private var downloadedLatestVersion = false
  private var versionOnServer: ByteArray? = null

  private lateinit var pushedLatch: CountDownLatch

  override fun prepareFileOnServer(snapshot: SettingsSnapshot) {
    ByteArrayOutputStream().use { stream ->
      SettingsSnapshotZipSerializer.serializeToStream(snapshot, stream)
      versionOnServer = stream.toByteArray()
    }
  }

  private fun getSnapshotOnServer(): SettingsSnapshot? {
    if (versionOnServer == null) {
      return null
    }
    val tempFile = FileUtil.createTempFile(UUID.randomUUID().toString(), null)
    FileUtil.writeToFile(tempFile, versionOnServer)
    return SettingsSnapshotZipSerializer.extractFromZip(tempFile.toPath())
  }

  override fun getVersionOnServer(): SettingsSnapshot? {
    return getSnapshotOnServer()
  }

  override fun checkServerState(): ServerState {
    return if (versionOnServer == null) ServerState.FileNotExists
    else if (downloadedLatestVersion) ServerState.UpToDate
    else ServerState.UpdateNeeded
  }

  override fun receiveUpdates(): UpdateResult {
    downloadedLatestVersion = true
    if (versionOnServer == null) {
      return UpdateResult.Error("Unexpectedly null update result")
    }
    return UpdateResult.Success(getVersionOnServer()!!, null)
  }

  override fun awaitForPush(): SettingsSnapshot? {
    pushedLatch = CountDownLatch(1)
    Assert.assertTrue("Didn't await until changes are pushed", pushedLatch.wait())
    return getVersionOnServer()
  }

  override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
    prepareFileOnServer(snapshot)
    if (::pushedLatch.isInitialized) pushedLatch.countDown()
    return SettingsSyncPushResult.Success(null)
  }

  override fun delete() {
    versionOnServer = null
  }
}
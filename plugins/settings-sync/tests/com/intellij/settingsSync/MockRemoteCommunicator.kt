package com.intellij.settingsSync

import com.intellij.openapi.util.io.FileUtil
import org.junit.Assert
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.CountDownLatch

internal class MockRemoteCommunicator : TestRemoteCommunicator() {
  private var version: Version? = null

  private lateinit var pushedLatch: CountDownLatch

  override fun prepareFileOnServer(snapshot: SettingsSnapshot) {
    ByteArrayOutputStream().use { stream ->
      SettingsSnapshotZipSerializer.serializeToStream(snapshot, stream)
      version = generateNewVersion(stream.toByteArray())
    }
  }

  override fun getVersionOnServer(): SettingsSnapshot? {
    return getSnapshotFromVersion(version?.content)
  }

  override fun checkServerState(): ServerState {
    return when {
      version == null -> ServerState.FileNotExists
      version!!.id == SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId -> ServerState.UpToDate
      else -> ServerState.UpdateNeeded
    }
  }

  override fun receiveUpdates(): UpdateResult {
    if (version == null) {
      return UpdateResult.NoFileOnServer
    }
    val snapshot = getSnapshotFromVersion(version!!.content)!!
    return if (snapshot.isDeleted()) UpdateResult.FileDeletedFromServer else UpdateResult.Success(snapshot, version!!.id)
  }

  override fun awaitForPush(): SettingsSnapshot? {
    pushedLatch = CountDownLatch(1)
    Assert.assertTrue("Didn't await until changes are pushed", pushedLatch.wait())
    return getVersionOnServer()
  }

  override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
    if (version?.id != expectedServerVersionId && !force) {
      return SettingsSyncPushResult.Rejected
    }
    prepareFileOnServer(snapshot)
    if (::pushedLatch.isInitialized) pushedLatch.countDown()
    return SettingsSyncPushResult.Success(version!!.id)
  }

  override fun delete() {
    version = null
  }

  private class Version(val content: ByteArray, val id: String)

  private fun generateNewVersion(content: ByteArray) = Version(content, UUID.randomUUID().toString())

  private fun getSnapshotFromVersion(version: ByteArray?): SettingsSnapshot? {
    if (version == null) {
      return null
    }
    val tempFile = FileUtil.createTempFile(UUID.randomUUID().toString(), null)
    FileUtil.writeToFile(tempFile, version)
    return SettingsSnapshotZipSerializer.extractFromZip(tempFile.toPath())
  }
}
package com.intellij.settingsSync

import com.intellij.settingsSync.CloudConfigServerCommunicator.Companion.createCloudConfigClient
import com.intellij.util.io.inputStream
import org.junit.Assert
import java.util.concurrent.CountDownLatch

internal class TestCloudConfigRemoteCommunicator : TestRemoteCommunicator() {

  private val cloudConfigServerCommunicator = CloudConfigServerCommunicator()

  private val versionContext = CloudConfigVersionContext()
  private val client = createCloudConfigClient(versionContext)

  private lateinit var pushedLatch: CountDownLatch

  override fun prepareFileOnServer(snapshot: SettingsSnapshot) {
    val zip = SettingsSnapshotZipSerializer.serializeToZip(snapshot)
    cloudConfigServerCommunicator.sendSnapshotFile(zip.inputStream(), null, true, versionContext, client)
  }

  override fun checkServerState(): ServerState = cloudConfigServerCommunicator.checkServerState()

  override fun receiveUpdates(): UpdateResult = cloudConfigServerCommunicator.receiveUpdates()

  override fun getVersionOnServer(): SettingsSnapshot? {
    val updateResult = receiveUpdates()
    return if (updateResult is UpdateResult.Success) updateResult.settingsSnapshot else null
  }

  override fun awaitForPush(): SettingsSnapshot? {
    pushedLatch = CountDownLatch(1)
    Assert.assertTrue("Didn't await until changes are pushed", pushedLatch.wait())
    return getVersionOnServer()
  }

  override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
    val result = cloudConfigServerCommunicator.push(snapshot, force, expectedServerVersionId)
    if (::pushedLatch.isInitialized) pushedLatch.countDown()
    return result
  }

  override fun delete() = cloudConfigServerCommunicator.delete()
}
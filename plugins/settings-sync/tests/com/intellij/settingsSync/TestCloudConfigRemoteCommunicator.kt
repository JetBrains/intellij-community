package com.intellij.settingsSync

import com.intellij.settingsSync.CloudConfigServerCommunicator.Companion.createCloudConfigClient
import com.intellij.util.io.inputStream
import java.time.Instant

internal class TestCloudConfigRemoteCommunicator : TestRemoteCommunicator() {

  private val cloudConfigServerCommunicator = CloudConfigServerCommunicator()

  private val versionContext = CloudConfigVersionContext()
  private val client = createCloudConfigClient(versionContext)

  override fun prepareFileOnServer(snapshot: SettingsSnapshot) {
    val zip = SettingsSnapshotZipSerializer.serializeToZip(snapshot)
    cloudConfigServerCommunicator.sendSnapshotFile(zip.inputStream(), null, true, versionContext, client)
  }

  override fun checkServerState(): ServerState = cloudConfigServerCommunicator.checkServerState()

  override fun receiveUpdates(): UpdateResult = cloudConfigServerCommunicator.receiveUpdates()

  override fun deleteAllFiles() {
    client.delete("*")
  }

  override fun getVersionOnServer(): SettingsSnapshot? =
    when (val updateResult = receiveUpdates()) {
      is UpdateResult.Success -> updateResult.settingsSnapshot
      UpdateResult.FileDeletedFromServer -> snapshotForDeletion()
      UpdateResult.NoFileOnServer -> null
      is UpdateResult.Error -> throw AssertionError(updateResult.message)
    }

  private fun snapshotForDeletion() =
    SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo(), isDeleted = true), emptySet(), null, emptyMap(), emptySet())

  override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
    val result = cloudConfigServerCommunicator.push(snapshot, force, expectedServerVersionId)
    settingsPushed(snapshot)
    return result
  }

  override fun createFile(filePath: String, content: String) {
    cloudConfigServerCommunicator.createFile(filePath, content)
  }

  override fun isFileExists(filePath: String): Boolean {
    return cloudConfigServerCommunicator.isFileExists(filePath)
  }

  override fun deleteFile(filePath: String) {
    cloudConfigServerCommunicator.deleteFile(filePath)
  }
}
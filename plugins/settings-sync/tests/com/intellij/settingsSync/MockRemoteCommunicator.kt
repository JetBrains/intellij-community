package com.intellij.settingsSync

import com.intellij.openapi.util.io.FileUtil
import com.intellij.settingsSync.CloudConfigServerCommunicator.Companion.getSnapshotFilePath
import java.io.ByteArrayOutputStream
import java.util.*

internal class MockRemoteCommunicator : TestRemoteCommunicator() {
  private val filesAndVersions = mutableMapOf<String, Version>()
  private val snapshotFile get() = filesAndVersions[getSnapshotFilePath()]

  override fun prepareFileOnServer(snapshot: SettingsSnapshot) {
    ByteArrayOutputStream().use { stream ->
      SettingsSnapshotZipSerializer.serializeToStream(snapshot, stream)
      filesAndVersions[getSnapshotFilePath()] = generateNewVersion(stream.toByteArray())
    }
  }

  override fun getVersionOnServer(): SettingsSnapshot? {
    return getSnapshotFromVersion(snapshotFile?.content)
  }

  override fun checkServerState(): ServerState {
    return when {
      snapshotFile == null -> ServerState.FileNotExists
      snapshotFile!!.id == SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId -> ServerState.UpToDate
      else -> ServerState.UpdateNeeded
    }
  }

  override fun receiveUpdates(): UpdateResult {
    if (snapshotFile == null) {
      return UpdateResult.NoFileOnServer
    }
    val snapshot = getSnapshotFromVersion(snapshotFile!!.content)!!
    return if (snapshot.isDeleted()) UpdateResult.FileDeletedFromServer else UpdateResult.Success(snapshot, snapshotFile!!.id)
  }

  override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
    if (snapshotFile?.id != expectedServerVersionId && !force) {
      return SettingsSyncPushResult.Rejected
    }
    prepareFileOnServer(snapshot)
    settingsPushed(snapshot)
    return SettingsSyncPushResult.Success(snapshotFile!!.id)
  }

  override fun createFile(filePath: String, content: String) {
    filesAndVersions[filePath] = generateNewVersion(content.toByteArray())
  }

  override fun isFileExists(filePath: String): Boolean {
    return filesAndVersions.containsKey(filePath)
  }

  override fun deleteFile(filePath: String) {
    filesAndVersions - filePath
  }

  override fun deleteAllFiles() {
    filesAndVersions.clear()
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
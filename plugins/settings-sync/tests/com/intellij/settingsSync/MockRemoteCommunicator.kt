package com.intellij.settingsSync

import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.cloudconfig.CloudConfigFileClientV2
import com.jetbrains.cloudconfig.Configuration
import com.jetbrains.cloudconfig.FileVersionInfo
import com.jetbrains.cloudconfig.auth.JbaTokenAuthProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

internal class MockRemoteCommunicator : TestRemoteCommunicator() {
  private val filesAndVersions = mutableMapOf<String, Version>()
  private val myClientV2 = lazy {
    object : CloudConfigFileClientV2(defaultUrl, Configuration().auth(JbaTokenAuthProvider("my-test-token")),
                                     CloudConfigServerCommunicator.DUMMY_ETAG_STORAGE, clientVersionContext) {
      override fun read(filePath: String): InputStream {
        val version = filesAndVersions[filePath] ?: throw IOException("file $filePath is not found")
        versionIdStorage.store(filePath, version.versionInfo.versionId)
        return ByteArrayInputStream(version.content)
      }

      override fun write(filePath: String, content: InputStream) {
        val version = Version(content.readAllBytes())
        filesAndVersions[filePath] = version
        versionIdStorage.store(filePath, version.versionInfo.versionId);
      }

      override fun delete(filePath: String) {
        filesAndVersions - filePath
        versionIdStorage.remove(filePath)
      }

      override fun getLatestVersion(filePath: String): FileVersionInfo? {
        val version = filesAndVersions[filePath] ?: return null
        return version.versionInfo
      }

      override fun getVersions(file: String): MutableList<FileVersionInfo> {
        return Collections.singletonList(getLatestVersion(file))
      }
    }
  }


  override val client: CloudConfigFileClientV2
    get() = myClientV2.value

  override fun prepareFileOnServer(snapshot: SettingsSnapshot) {
    ByteArrayOutputStream().use { stream ->
      SettingsSnapshotZipSerializer.serializeToStream(snapshot, stream)
      val content = stream.toByteArray()
      client.write(currentSnapshotFilePath(), ByteArrayInputStream(content))
    }
  }

  override fun getVersionOnServer(): SettingsSnapshot? {
    val snapshotFilePath = currentSnapshotFilePath()
    return getSnapshotFromVersion(filesAndVersions[snapshotFilePath]?.content)
  }

  override fun deleteAllFiles() {
    filesAndVersions.clear()
  }

  private class Version(val content: ByteArray, val versionInfo: FileVersionInfo) {
    constructor(content: ByteArray) : this(content, MyVersionInfo())
  }

  private class MyVersionInfo : FileVersionInfo() {
    private val versionId: String = versionRef.incrementAndGet().toString()
    private val modifiedDate = Date(System.currentTimeMillis())
    override fun getVersionId() = versionId

    override fun getModifiedDate() = modifiedDate

    override fun isLatest() = true
  }

  private fun getSnapshotFromVersion(version: ByteArray?): SettingsSnapshot? {
    if (version == null) {
      return null
    }
    val tempFile = FileUtil.createTempFile(UUID.randomUUID().toString(), null)
    FileUtil.writeToFile(tempFile, version)
    return SettingsSnapshotZipSerializer.extractFromZip(tempFile.toPath())
  }

  companion object {
    private val versionRef = AtomicInteger()
  }
}
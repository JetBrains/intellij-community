package com.intellij.settingsSync

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.communicator.SettingsSyncCommunicatorProvider
import com.intellij.settingsSync.communicator.SettingsSyncUserData
import org.junit.Assert
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.isInitialized

internal class MockRemoteCommunicator : AbstractServerCommunicator() {
  private val filesAndVersions = mutableMapOf<String, Version>()
  private val versionIdStorage = mutableMapOf<String, String>()
  private val LOG = logger<MockRemoteCommunicator>()
  var isConnected = true

  private lateinit var pushedLatch: CountDownLatch
  private lateinit var pushedSnapshot: SettingsSnapshot

  fun settingsPushed(snapshot: SettingsSnapshot) {
    if (::pushedLatch.isInitialized) {
      pushedSnapshot = snapshot
      pushedLatch.countDown()
    }
  }



  override fun requestSuccessful() {
    // do nothing
  }

  override fun handleRemoteError(e: Throwable): String {
    // do nothing yet
    return e.message ?: "unknown error"
  }

  override fun readFileInternal(snapshotFilePath: String): Pair<InputStream?, String?> {
    checkConnected()
    val version = filesAndVersions[snapshotFilePath] ?: throw IOException("file $snapshotFilePath is not found")
    versionIdStorage.put(snapshotFilePath, version.versionId)
    LOG.warn("Put version '${version.versionId}' for file $snapshotFilePath (after read)")
    return Pair(ByteArrayInputStream(version.content), version.versionId)
  }

  override fun writeFileInternal(filePath: String, versionId: String?, content: InputStream): String? {
    checkConnected()
    val currentVersion = filesAndVersions[filePath]
    if (versionId != null && currentVersion != null && currentVersion.versionId != versionId) {
      throw InvalidVersionIdException("Expected version $versionId, but actual is ${currentVersion.versionId}")
    }
    val version = Version(content.readAllBytes())
    filesAndVersions[filePath] = version
    versionIdStorage.put(filePath, version.versionId);
    LOG.warn("Put version '${version.versionId}' for file $filePath (after write)")
    return version.versionId
  }

  override fun getLatestVersion(filePath: String): String? {
    checkConnected()
    val version = filesAndVersions[filePath] ?: return null
    return version.versionId
  }

  override fun deleteFileInternal(filePath: String) {
    checkConnected()
    filesAndVersions - filePath
    versionIdStorage.remove(filePath)
    LOG.warn("Removed version for file $filePath")
  }

  fun awaitForPush(testExecution: () -> Unit): SettingsSnapshot {
    pushedLatch = CountDownLatch(1)
    testExecution()
    Assert.assertTrue("Didn't await until changes are pushed", pushedLatch.wait())
    return pushedSnapshot
  }

  override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
    val push = super.push(snapshot, force, expectedServerVersionId)
    settingsPushed(snapshot)
    return push
  }

  fun prepareFileOnServer(snapshot: SettingsSnapshot) {
    ByteArrayOutputStream().use { stream ->
      SettingsSnapshotZipSerializer.serializeToStream(snapshot, stream)
      val content = stream.toByteArray()
      val (snapshotFilePath, _) = currentSnapshotFilePath() ?: return
      versionIdStorage.remove(snapshotFilePath)
      filesAndVersions.remove(snapshotFilePath)
      writeFileInternal(snapshotFilePath, System.nanoTime().toString(), ByteArrayInputStream(content))
    }
  }
  private fun getSnapshotFromVersion(version: ByteArray?): SettingsSnapshot? {
    if (version == null) {
      return null
    }
    val tempFile = FileUtil.createTempFile(UUID.randomUUID().toString(), null)
    FileUtil.writeToFile(tempFile, version)
    return SettingsSnapshotZipSerializer.extractFromZip(tempFile.toPath())
  }

  fun getVersionOnServer(): SettingsSnapshot? {
    val (snapshotFilePath, _) = currentSnapshotFilePath() ?: return null
    return getSnapshotFromVersion(filesAndVersions[snapshotFilePath]?.content)
  }

  fun deleteAllFiles() {
    filesAndVersions.clear()
  }

  fun ideCrossSyncState(): Boolean? {
    val (_, crossSyncState) = currentSnapshotFilePath() ?: Pair(null, null)

    return crossSyncState
  }


  private class Version(val content: ByteArray, val versionId: String) {
    constructor(content: ByteArray) : this(content, System.nanoTime().toString())
  }

  private fun checkConnected() {
    if (!isConnected) {
      throw IOException(DISCONNECTED_ERROR)
    }
  }

  companion object {
    private val versionRef = AtomicInteger()
    val snapshotForDeletion =
      SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo(), isDeleted = true), emptySet(), null, emptyMap(), emptySet())
    const val DISCONNECTED_ERROR = "disconnected"
  }
}

internal class MockCommunicatorProvider (
  private val remoteCommunicator: SettingsSyncRemoteCommunicator,
  override val authService: SettingsSyncAuthService = MockAuthService(SettingsSyncUserData("", "")),
): SettingsSyncCommunicatorProvider {
  override val providerCode: String
    get() = "MOCK"

  override fun createCommunicator(): SettingsSyncRemoteCommunicator? = remoteCommunicator
}

internal class MockAuthService (
  private val userData: SettingsSyncUserData
): SettingsSyncAuthService {
  override val providerCode: String
    get() = "MOCK"

  override fun login() {
    // do nothing
  }

  override fun isLoggedIn(): Boolean {
    return true
  }

  override fun getUserData(): SettingsSyncUserData {
    return userData
  }

  override fun isLoginAvailable(): Boolean {
    return true
  }

}
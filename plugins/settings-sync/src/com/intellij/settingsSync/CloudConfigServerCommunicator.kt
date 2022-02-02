package com.intellij.settingsSync

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.util.io.delete
import com.intellij.util.io.inputStream
import com.jetbrains.cloudconfig.CloudConfigFileClientV2
import com.jetbrains.cloudconfig.Configuration
import com.jetbrains.cloudconfig.ETagStorage
import com.jetbrains.cloudconfig.HeaderStorage
import com.jetbrains.cloudconfig.auth.JbaTokenAuthProvider
import com.jetbrains.cloudconfig.exception.InvalidVersionIdException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val END_POINT = "https://stgn.cloudconfig.jetbrains.com/cloudconfig" // todo choose between production and staging via a system property

private const val TIMEOUT = 10000

internal class CloudConfigServerCommunicator : SettingsSyncRemoteCommunicator {

  private val client get() = _client.value
  private val _client = lazy {
    val conf = createConfiguration()
    CloudConfigFileClientV2(END_POINT, conf, DUMMY_ETAG_STORAGE, clientVersionContext)
  }

  private val currentVersionOfFiles = mutableMapOf<String, String>() // todo persist this information
  private val clientVersionContext = VersionContext()

  private fun createConfiguration(): Configuration {
    val userId = SettingsSyncAuthService.getInstance().getUserData()?.id
    if (userId == null) {
      throw SettingsSyncAuthException("Authentication required")
    }
    return Configuration().connectTimeout(TIMEOUT).readTimeout(TIMEOUT).auth(JbaTokenAuthProvider(userId))
  }

  private fun receiveSnapshotFile(): InputStream? {
    // todo remove this explicit request after client.read will be fixed to accept null version
    val version = client.getLatestVersion(SETTINGS_SYNC_SNAPSHOT_ZIP)?.versionId ?: return null

    return clientVersionContext.doWithVersion(SETTINGS_SYNC_SNAPSHOT_ZIP, version) {
      client.read(SETTINGS_SYNC_SNAPSHOT_ZIP)
    }
  }

  private fun sendSnapshotFile(inputStream: InputStream) {
    val currentVersion = getCurrentVersion()
    LOG.info("Sending $SETTINGS_SYNC_SNAPSHOT_ZIP, current version: $currentVersion")
    if (currentVersion != null) {
      clientVersionContext.doWithVersion(SETTINGS_SYNC_SNAPSHOT_ZIP, currentVersion) {
        client.write(SETTINGS_SYNC_SNAPSHOT_ZIP, inputStream)
      }
    }
    else {
      // no version neither locally nor on the server => this is a fresh push => simply send the push
      client.write(SETTINGS_SYNC_SNAPSHOT_ZIP, inputStream)
    }
  }

  private fun getCurrentVersion(): String? {
    val rememberedVersion = currentVersionOfFiles[SETTINGS_SYNC_SNAPSHOT_ZIP]
    if (rememberedVersion != null) return rememberedVersion

    // todo in this case we should update first and not just overwrite
    val serverVersion = client.getLatestVersion(SETTINGS_SYNC_SNAPSHOT_ZIP)?.versionId
    if (serverVersion != null) {
      LOG.warn("Current version is null, using the version from the server: $serverVersion")
      return serverVersion
    }

    LOG.info("No settings file on the server")
    return null
  }

  override fun isUpdateNeeded(): Boolean {
    try {
      val version = client.getLatestVersion(SETTINGS_SYNC_SNAPSHOT_ZIP).versionId
      return version != currentVersionOfFiles[SETTINGS_SYNC_SNAPSHOT_ZIP]
    }
    catch (e: Throwable) {
      handleRemoteError(e)
      return false
    }
  }

  override fun receiveUpdates(): UpdateResult {
    LOG.info("Receiving settings snapshot from the cloud config server...")
    try {
      val stream = receiveSnapshotFile()
      if (stream == null) {
        LOG.info("$SETTINGS_SYNC_SNAPSHOT_ZIP not found on the server")
        return UpdateResult.NoFileOnServer
      }

      val tempFile = FileUtil.createTempFile(SETTINGS_SYNC_SNAPSHOT, UUID.randomUUID().toString() + ".zip")
      try {
        FileUtil.writeToFile(tempFile, stream.readAllBytes())
        val snapshot = extractZipFile(tempFile.toPath())
        return if (snapshot.isEmpty()) UpdateResult.NoFileOnServer else UpdateResult.Success(snapshot)
      }
      finally {
        FileUtil.delete(tempFile)
      }
    }
    catch (e: Throwable) {
      val message = handleRemoteError(e)
      return UpdateResult.Error(message)
    }
  }

  override fun push(snapshot: SettingsSnapshot): SettingsSyncPushResult {
    LOG.info("Pushing setting snapshot to the cloud config server...")
    val zip = try {
      prepareTempZipFile(snapshot)
    }
    catch (e: Throwable) {
      LOG.warn(e)
      return SettingsSyncPushResult.Error(e.message ?: "Couldn't prepare zip file")
    }

    try {
      sendSnapshotFile(zip.inputStream())
      return SettingsSyncPushResult.Success
    }
    catch (ive: InvalidVersionIdException) {
      LOG.info("Rejected: version doesn't match the version on server: ${ive.message}")
      return SettingsSyncPushResult.Rejected
    }
    // todo handle authentication failure: propose to login
    catch (e: Throwable) {
      val message = handleRemoteError(e)
      return SettingsSyncPushResult.Error(message)
    }
    finally {
      try {
        zip.delete()
      }
      catch (e: Throwable) {
        LOG.warn(e)
      }
    }
  }

  private fun handleRemoteError(e: Throwable): String {
    val defaultMessage = "Error during communication with server"
    if (e is IOException) {
      LOG.warn(e)
      return e.message ?: defaultMessage
    }
    else {
      LOG.error(e)
      return defaultMessage
    }
  }

  fun downloadSnapshot(): File? {
    val stream = receiveSnapshotFile()
    if (stream == null) {
      LOG.info("$SETTINGS_SYNC_SNAPSHOT_ZIP not found on the server")
      return null
    }

    try {
      val currentDate = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
      val tempFile = FileUtil.createTempFile("settings.sync.snapshot.$currentDate.zip", null)
      FileUtil.writeToFile(tempFile, stream.readAllBytes())
      return tempFile
    }
    catch (e: Throwable) {
      LOG.error(e)
      return null
    }
  }

  private inner class VersionContext : HeaderStorage {
    private val contextVersionMap = mutableMapOf<String, String>()
    private val lock = ReentrantLock()

    override fun get(path: String): String? {
      return contextVersionMap[path]
    }

    override fun store(path: String, value: String) {
      contextVersionMap[path] = value
    }

    fun <T> doWithVersion(path: String, version: String, function: () -> T): T {
      return lock.withLock {
        contextVersionMap[path] = version
        val result = function()

        val actualVersion: String? = contextVersionMap[path]
        if (actualVersion == null) {
          LOG.error("Version not found for $path")
        }
        else {
          currentVersionOfFiles[path] = actualVersion
        }

        result
      }
    }
  }

  companion object {
    private val LOG = logger<CloudConfigServerCommunicator>()

    private val DUMMY_ETAG_STORAGE: ETagStorage = object : ETagStorage {
      override fun get(path: String): String? {
        return null
      }

      override fun store(path: String, value: String) {
        // do nothing
      }
    }
  }
}
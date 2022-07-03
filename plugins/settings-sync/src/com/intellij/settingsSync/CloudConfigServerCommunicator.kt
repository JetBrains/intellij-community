package com.intellij.settingsSync

import com.intellij.ide.plugins.PluginManagerCore.isRunningFromSources
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.util.io.delete
import com.intellij.util.io.inputStream
import com.jetbrains.cloudconfig.*
import com.jetbrains.cloudconfig.auth.JbaTokenAuthProvider
import com.jetbrains.cloudconfig.exception.InvalidVersionIdException
import java.io.IOException
import java.io.InputStream
import java.util.*

internal const val SETTINGS_SYNC_SNAPSHOT = "settings.sync.snapshot"
internal const val SETTINGS_SYNC_SNAPSHOT_ZIP = "$SETTINGS_SYNC_SNAPSHOT.zip"

private const val TIMEOUT = 10000

internal class CloudConfigServerCommunicator : SettingsSyncRemoteCommunicator {

  private val client get() = _client.value
  private val _client = lazy { createCloudConfigClient(clientVersionContext) }
  private val clientVersionContext = CloudConfigVersionContext()

  private var knownVersionOfSnapshotZip : String? = null

  private fun receiveSnapshotFile(): InputStream? {
    return clientVersionContext.doWithVersion(null) {
      val result = client.read(SETTINGS_SYNC_SNAPSHOT_ZIP)
      rememberLatestVersion()
      result
    }
  }

  // executed under the lock of the VersionContext
  private fun rememberLatestVersion() {
    val actualVersion: String? = clientVersionContext.get(SETTINGS_SYNC_SNAPSHOT_ZIP)
    if (actualVersion == null) {
      LOG.warn("Version not stored in the context for $SETTINGS_SYNC_SNAPSHOT_ZIP")
    }
    else {
      knownVersionOfSnapshotZip = actualVersion
    }
  }

  private fun sendSnapshotFile(inputStream: InputStream, force: Boolean): SettingsSyncPushResult {
    val versionToPush: String?
    if (force) {
      // get the latest server version: pushing with it will overwrite the file in any case
      versionToPush = getLatestVersion()?.versionId
    }
    else {
      val rememberedVersion = knownVersionOfSnapshotZip
      if (rememberedVersion != null) {
        versionToPush = rememberedVersion
      }
      else {
        val serverVersion = getLatestVersion()?.versionId
        if (serverVersion == null) {
          // no file on the server => just push it there
          versionToPush = null
        }
        else {
          // we didn't store the server version locally yet => reject the push to avoid overwriting the server version;
          // the next update after the rejected push will store the version information, and subsequent push will be successful.
          return SettingsSyncPushResult.Rejected
        }
      }
    }

    clientVersionContext.doWithVersion(versionToPush) {
      client.write(SETTINGS_SYNC_SNAPSHOT_ZIP, inputStream)
      rememberLatestVersion()
    }
    // errors are thrown as exceptions, and are handled above
    return SettingsSyncPushResult.Success
  }

  override fun checkServerState(): ServerState {
    try {
      val latestVersion = client.getLatestVersion(SETTINGS_SYNC_SNAPSHOT_ZIP)
      LOG.info("Latest version info: $latestVersion")
      when (latestVersion?.versionId) {
        null -> return ServerState.FileNotExists
        knownVersionOfSnapshotZip -> return ServerState.UpToDate
        else -> return ServerState.UpdateNeeded
      }
    }
    catch (e: Throwable) {
      val message = handleRemoteError(e)
      return ServerState.Error(message)
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
        val snapshot = SettingsSnapshotZipSerializer.extractFromZip(tempFile.toPath())
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

  override fun push(snapshot: SettingsSnapshot, force: Boolean): SettingsSyncPushResult {
    LOG.info("Pushing setting snapshot to the cloud config server...")
    val zip = try {
      SettingsSnapshotZipSerializer.serializeToZip(snapshot)
    }
    catch (e: Throwable) {
      LOG.warn(e)
      return SettingsSyncPushResult.Error(e.message ?: "Couldn't prepare zip file")
    }

    try {
      return sendSnapshotFile(zip.inputStream(), force)
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

  fun downloadSnapshot(version: FileVersionInfo): InputStream? {
    val stream = clientVersionContext.doWithVersion(version.versionId) {
      client.read(SETTINGS_SYNC_SNAPSHOT_ZIP)
    }

    if (stream == null) {
      LOG.info("$SETTINGS_SYNC_SNAPSHOT_ZIP not found on the server")
    }

    return stream
  }

  fun getLatestVersion(): FileVersionInfo? {
    return client.getLatestVersion(SETTINGS_SYNC_SNAPSHOT_ZIP)
  }

  @Throws(IOException::class)
  override fun delete() {
    knownVersionOfSnapshotZip = null
    client.delete(SETTINGS_SYNC_SNAPSHOT_ZIP)
  }

  @Throws(Exception::class)
  fun fetchHistory(): List<FileVersionInfo> {
    return client.getVersions(SETTINGS_SYNC_SNAPSHOT_ZIP)
  }

  companion object {
    internal const val DEFAULT_PRODUCTION_URL = "https://cloudconfig.jetbrains.com/cloudconfig"
    private const val DEFAULT_DEBUG_URL = "https://stgn.cloudconfig.jetbrains.com/cloudconfig"
    internal const val URL_PROPERTY = "idea.settings.sync.cloud.url"

    internal val url get() = _url.value

    private val _url = lazy {
      val explicitUrl = System.getProperty(URL_PROPERTY)
      when {
        explicitUrl != null -> {
          LOG.info("Using URL from properties: $explicitUrl")
          explicitUrl
        }
        isRunningFromSources() -> DEFAULT_DEBUG_URL
        else -> DEFAULT_PRODUCTION_URL
      }
    }

    internal fun createCloudConfigClient(versionContext: CloudConfigVersionContext): CloudConfigFileClientV2 {
      val conf = createConfiguration()
      return CloudConfigFileClientV2(url, conf, DUMMY_ETAG_STORAGE, versionContext)
    }

    private fun createConfiguration(): Configuration {
      val userId = SettingsSyncAuthService.getInstance().getUserData()?.id
      if (userId == null) {
        throw SettingsSyncAuthException("Authentication required")
      }
      return Configuration().connectTimeout(TIMEOUT).readTimeout(TIMEOUT).auth(JbaTokenAuthProvider(userId))
    }

    private val LOG = logger<CloudConfigServerCommunicator>()

    private val DUMMY_ETAG_STORAGE: ETagStorage = object : ETagStorage {
      override fun get(path: String): String? {
        return null
      }

      override fun store(path: String, value: String) {
        // do nothing
      }

      override fun remove(path: String?) {
        // do nothing
      }
    }
  }
}

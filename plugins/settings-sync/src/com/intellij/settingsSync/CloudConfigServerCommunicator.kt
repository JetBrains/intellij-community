package com.intellij.settingsSync

import com.intellij.ide.plugins.PluginManagerCore.isRunningFromSources
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.JBAccountInfoService
import com.intellij.util.io.delete
import com.intellij.util.io.inputStream
import com.jetbrains.cloudconfig.*
import com.jetbrains.cloudconfig.auth.JbaJwtTokenAuthProvider
import com.jetbrains.cloudconfig.exception.InvalidVersionIdException
import org.jetbrains.annotations.VisibleForTesting
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.*

internal const val SETTINGS_SYNC_SNAPSHOT = "settings.sync.snapshot"
internal const val SETTINGS_SYNC_SNAPSHOT_ZIP = "$SETTINGS_SYNC_SNAPSHOT.zip"

private const val CONNECTION_TIMEOUT_MS = 10000
private const val READ_TIMEOUT_MS = 50000

internal class CloudConfigServerCommunicator : SettingsSyncRemoteCommunicator {

  private val snapshotFilePath get() = getSnapshotFilePath()

  internal val client get() = _client.value
  private val _client = lazy { createCloudConfigClient(clientVersionContext) }
  private val clientVersionContext = CloudConfigVersionContext()

  private fun receiveSnapshotFile(): Pair<InputStream?, String?> {
    return clientVersionContext.doWithVersion(snapshotFilePath, null) { filePath ->
      try {
        val stream = client.read(filePath)

        val actualVersion: String? = clientVersionContext.get(filePath)
        if (actualVersion == null) {
          LOG.warn("Version not stored in the context for $filePath")
        }

        Pair(stream, actualVersion)
      }
      catch (fileNotFound : FileNotFoundException) {
        Pair(null, null)
      }
    }
  }

  private fun sendSnapshotFile(inputStream: InputStream, knownServerVersion: String?, force: Boolean): SettingsSyncPushResult {
    return sendSnapshotFile(inputStream, knownServerVersion, force, clientVersionContext, client)
  }

  @VisibleForTesting
  internal fun sendSnapshotFile(
    inputStream: InputStream,
    knownServerVersion: String?,
    force: Boolean,
    versionContext: CloudConfigVersionContext,
    cloudConfigClient: CloudConfigFileClientV2
  ): SettingsSyncPushResult {
    val versionToPush: String?
    if (force) {
      // get the latest server version: pushing with it will overwrite the file in any case
      versionToPush = getLatestVersion(snapshotFilePath)?.versionId
    }
    else {
      if (knownServerVersion != null) {
        versionToPush = knownServerVersion
      }
      else {
        val serverVersion = getLatestVersion(snapshotFilePath)?.versionId
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

    val serverVersionId = versionContext.doWithVersion(snapshotFilePath, versionToPush) { filePath ->
      cloudConfigClient.write(filePath, inputStream)

      val actualVersion: String? = versionContext.get(filePath)
      if (actualVersion == null) {
        LOG.warn("Version not stored in the context for $filePath")
      }
      actualVersion
    }
    // errors are thrown as exceptions, and are handled above
    return SettingsSyncPushResult.Success(serverVersionId)
  }

  override fun checkServerState(): ServerState {
    try {
      val latestVersion = client.getLatestVersion(snapshotFilePath)
      LOG.debug("Latest version info: $latestVersion")
      when (latestVersion?.versionId) {
        null -> return ServerState.FileNotExists
        SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId -> return ServerState.UpToDate
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
      val (stream, version) = receiveSnapshotFile()
      if (stream == null) {
        LOG.info("$snapshotFilePath not found on the server")
        return UpdateResult.NoFileOnServer
      }

      val tempFile = FileUtil.createTempFile(SETTINGS_SYNC_SNAPSHOT, UUID.randomUUID().toString() + ".zip")
      try {
        FileUtil.writeToFile(tempFile, stream.readAllBytes())
        val snapshot = SettingsSnapshotZipSerializer.extractFromZip(tempFile.toPath())
        return if (snapshot.isDeleted()) UpdateResult.FileDeletedFromServer else UpdateResult.Success(snapshot, version)
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

  override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
    LOG.info("Pushing setting snapshot to the cloud config server...")
    val zip = try {
      SettingsSnapshotZipSerializer.serializeToZip(snapshot)
    }
    catch (e: Throwable) {
      LOG.warn(e)
      return SettingsSyncPushResult.Error(e.message ?: "Couldn't prepare zip file")
    }

    try {
      return sendSnapshotFile(zip.inputStream(), expectedServerVersionId, force)
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

  fun downloadSnapshot(filePath: String, version: FileVersionInfo): InputStream? {
    val stream = clientVersionContext.doWithVersion(filePath, version.versionId) { path ->
      client.read(path)
    }

    if (stream == null) {
      LOG.info("$snapshotFilePath not found on the server")
    }

    return stream
  }

  override fun createFile(filePath: String, content: String) {
    client.write(filePath, content.byteInputStream())
  }

  private fun getLatestVersion(filePath: String): FileVersionInfo? {
    return client.getLatestVersion(filePath)
  }

  @Throws(IOException::class)
  override fun deleteFile(filePath: String) {
    SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId = null
    client.delete(filePath)
  }

  override fun isFileExists(filePath: String): Boolean {
    return client.getLatestVersion(filePath) != null
  }

  @Throws(Exception::class)
  fun fetchHistory(filePath: String): List<FileVersionInfo> {
    return client.getVersions(filePath)
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

    @VisibleForTesting
    internal fun getSnapshotFilePath() = if (SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled) {
      SETTINGS_SYNC_SNAPSHOT_ZIP
    }
    else {
      "${ApplicationNamesInfo.getInstance().productName.lowercase()}/$SETTINGS_SYNC_SNAPSHOT_ZIP"
    }

    private fun createConfiguration(): Configuration {
      val idToken = JBAccountInfoService.getInstance()?.idToken
      if (idToken == null) {
        throw SettingsSyncAuthException("Authentication required")
      }
      return Configuration().connectTimeout(CONNECTION_TIMEOUT_MS).readTimeout(READ_TIMEOUT_MS).auth(JbaJwtTokenAuthProvider(idToken))
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

package com.intellij.settingsSync

import com.intellij.ide.plugins.PluginManagerCore.isRunningFromSources
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.delete
import com.intellij.util.resettableLazy
import com.jetbrains.cloudconfig.*
import com.jetbrains.cloudconfig.auth.JbaJwtTokenAuthProvider
import com.jetbrains.cloudconfig.exception.InvalidVersionIdException
import com.jetbrains.cloudconfig.exception.UnauthorizedException
import org.jdom.JDOMException
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.inputStream

internal const val CROSS_IDE_SYNC_MARKER_FILE = "cross-ide-sync-enabled"
internal const val SETTINGS_SYNC_SNAPSHOT = "settings.sync.snapshot"
internal const val SETTINGS_SYNC_SNAPSHOT_ZIP = "$SETTINGS_SYNC_SNAPSHOT.zip"

private const val CONNECTION_TIMEOUT_MS = 10000
private const val READ_TIMEOUT_MS = 50000

internal open class CloudConfigServerCommunicator(serverUrl: String? = null) : SettingsSyncRemoteCommunicator {

  protected open val _userId = resettableLazy { SettingsSyncAuthService.getInstance().getUserData()?.id }
  protected open val _idToken = resettableLazy { SettingsSyncAuthService.getInstance().getAccountInfoService()?.idToken }
  internal val userId get() = _userId.value
  private val idToken get() = _idToken.value
  protected val clientVersionContext = CloudConfigVersionContext()
  internal var _client = resettableLazy { createCloudConfigClient(serverUrl ?: defaultUrl, clientVersionContext) }
    @TestOnly set
  internal open val client get() = _client.value

  private val lastRemoteErrorRef = AtomicReference<Throwable>()

  init {
    SettingsSyncEvents.getInstance().addListener(
      object : SettingsSyncEventListener {
        override fun loginStateChanged() {
          _userId.reset()
          _idToken.reset()
          _client.reset()
        }
      }
    )
  }

  @VisibleForTesting
  @Throws(IOException::class, UnauthorizedException::class)
  protected fun currentSnapshotFilePath(): String? {
    try {
      val crossIdeSyncEnabled = isFileExists(CROSS_IDE_SYNC_MARKER_FILE)
      if (crossIdeSyncEnabled != SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled) {
        LOG.info("Cross-IDE sync status on server is: ${enabledOrDisabled(crossIdeSyncEnabled)}. Updating local settings with it.")
        SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled = crossIdeSyncEnabled
      }
      return if (crossIdeSyncEnabled) {
        SETTINGS_SYNC_SNAPSHOT_ZIP
      }
      else {
        "${ApplicationNamesInfo.getInstance().productName.lowercase()}/$SETTINGS_SYNC_SNAPSHOT_ZIP"
      }
    }
    catch (e: Throwable) {
      if (e is IOException || e is UnauthorizedException) {
        throw e
      }
      else {
        LOG.warn("Couldn't check if $CROSS_IDE_SYNC_MARKER_FILE exists", e)
        return null
      }
    }
  }

  @Throws(IOException::class)
  private fun receiveSnapshotFile(snapshotFilePath: String): Pair<InputStream?, String?> {
    return clientVersionContext.doWithVersion(snapshotFilePath, null) { filePath ->
      try {
        val stream = client.read(filePath)

        val actualVersion: String? = clientVersionContext.get(filePath)
        if (actualVersion == null) {
          LOG.warn("Version not stored in the context for $filePath")
        }

        Pair(stream, actualVersion)
      }
      catch (fileNotFound: FileNotFoundException) {
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
    val snapshotFilePath: String
    val defaultMessage = "Unknown during checking $CROSS_IDE_SYNC_MARKER_FILE"
    try {
      snapshotFilePath = currentSnapshotFilePath() ?: return SettingsSyncPushResult.Error(defaultMessage)
    }
    catch (ioe: IOException) {
      return SettingsSyncPushResult.Error(ioe.message ?: defaultMessage)
    }

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
    val userIdInRequest = userId
    try {
      val snapshotFilePath = currentSnapshotFilePath() ?: return ServerState.Error("Unknown error during checkServerState")
      val latestVersion = client.getLatestVersion(snapshotFilePath)
      LOG.debug("Latest version info: $latestVersion")
      clearLastRemoteError()
      when (latestVersion?.versionId) {
        null -> return ServerState.FileNotExists
        SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId -> return ServerState.UpToDate
        else -> return ServerState.UpdateNeeded
      }
    }
    catch (e: Throwable) {
      val message = handleRemoteError(e, userIdInRequest)
      return ServerState.Error(message)
    }
  }

  override fun receiveUpdates(): UpdateResult {
    LOG.info("Receiving settings snapshot from the cloud config server...")
    val userIdInRequest = userId
    try {
      val snapshotFilePath = currentSnapshotFilePath() ?: return UpdateResult.Error("Unknown error during receiveUpdates")
      val (stream, version) = receiveSnapshotFile(snapshotFilePath)
      clearLastRemoteError()
      if (stream == null) {
        LOG.info("$snapshotFilePath not found on the server")
        return UpdateResult.NoFileOnServer
      }

      val tempFile = FileUtil.createTempFile(SETTINGS_SYNC_SNAPSHOT, UUID.randomUUID().toString() + ".zip")
      try {
        FileUtil.writeToFile(tempFile, stream.readAllBytes())
        val snapshot = SettingsSnapshotZipSerializer.extractFromZip(tempFile.toPath())
        if (snapshot == null) {
          LOG.info("cannot extract snapshot from tempFile ${tempFile.toPath()}. Implying there's no snapshot")
          return UpdateResult.NoFileOnServer
        } else {
          return if (snapshot.isDeleted()) UpdateResult.FileDeletedFromServer else UpdateResult.Success(snapshot, version)
        }
      }
      finally {
        FileUtil.delete(tempFile)
      }
    }
    catch (e: Throwable) {
      val message = handleRemoteError(e, userIdInRequest)
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

    val userIdInRequest = userId
    try {
      val pushResult = sendSnapshotFile(zip.inputStream(), expectedServerVersionId, force)
      clearLastRemoteError()
      return pushResult
    }
    catch (ive: InvalidVersionIdException) {
      LOG.info("Rejected: version doesn't match the version on server: ${ive.message}")
      return SettingsSyncPushResult.Rejected
    }
    catch (e: Throwable) {
      val message = handleRemoteError(e, userIdInRequest)
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

  private fun clearLastRemoteError(){
    if (lastRemoteErrorRef.get() != null) {
      LOG.info("Connection to setting sync server is restored")
    }
    lastRemoteErrorRef.set(null)
  }

  private fun handleRemoteError(e: Throwable, userIdInRequest: String?): String {
    val defaultMessage = "Error during communication with server"
    if (e is IOException) {
      if (lastRemoteErrorRef.get()?.message != e.message) {
        lastRemoteErrorRef.set(e)
        LOG.warn("$defaultMessage: ${e.message}")
      }
    }
    else if (e is UnauthorizedException) {
      if (userIdInRequest != null) {
        SettingsSyncAuthService.getInstance().invalidateJBA(userIdInRequest)
      }
    }
    else {
      LOG.error(e)
    }
    return e.message ?: defaultMessage
  }

  fun downloadSnapshot(filePath: String, version: FileVersionInfo): InputStream? {
    val stream = clientVersionContext.doWithVersion(filePath, version.versionId) { path ->
      client.read(path)
    }

    if (stream == null) {
      LOG.info("$filePath not found on the server")
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

  @Throws(IOException::class)
  override fun isFileExists(filePath: String): Boolean {
    return client.getLatestVersion(filePath) != null
  }

  @Throws(Exception::class)
  fun fetchHistory(filePath: String): List<FileVersionInfo> {
    return client.getVersions(filePath)
  }

  internal fun createCloudConfigClient(url: String, versionContext: CloudConfigVersionContext): CloudConfigFileClientV2 {
    val conf = createConfiguration()
    return CloudConfigFileClientV2(url, conf, DUMMY_ETAG_STORAGE, versionContext)
  }

  private fun createConfiguration(): Configuration {
    return Configuration().connectTimeout(CONNECTION_TIMEOUT_MS).readTimeout(READ_TIMEOUT_MS)
      .auth(JbaJwtTokenAuthProvider(idToken ?: throw SettingsSyncAuthException("Authentication required")))
  }

  companion object {
    private const val URL_PROVIDER = "https://www.jetbrains.com/config/IdeaCloudConfig.xml"
    private const val DEFAULT_PRODUCTION_URL = "https://cloudconfig.jetbrains.com/cloudconfig"
    private const val DEFAULT_DEBUG_URL = "https://stgn.cloudconfig.jetbrains.com/cloudconfig"
    private const val URL_PROPERTY = "idea.settings.sync.cloud.url"

    internal val defaultUrl get() = _url.value

    private val _url = lazy {
      val explicitUrl = System.getProperty(URL_PROPERTY)
      when {
        explicitUrl != null -> {
          LOG.info("Using SettingSync server URL (from properties): $explicitUrl")
          explicitUrl
        }
        isRunningFromSources() -> {
          LOG.info("Using SettingSync server URL (DEBUG): $DEFAULT_DEBUG_URL")
          DEFAULT_DEBUG_URL
        }
        else -> getProductionUrl()
      }
    }

    private fun getProductionUrl(): String {
      val configUrl = HttpRequests.request(URL_PROVIDER)
        .productNameAsUserAgent()
        .connect(HttpRequests.RequestProcessor { request: HttpRequests.Request ->
          try {
            val documentElement = JDOMUtil.load(request.inputStream)
            documentElement.getAttributeValue("baseUrl")
          }
          catch (e: JDOMException) {
            throw IOException(e)
          }
        }, DEFAULT_PRODUCTION_URL, LOG)
      LOG.info("Using SettingSync server URL: $configUrl")
      return configUrl
    }

    private val LOG = logger<CloudConfigServerCommunicator>()

    @VisibleForTesting
    internal val DUMMY_ETAG_STORAGE: ETagStorage = object : ETagStorage {
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

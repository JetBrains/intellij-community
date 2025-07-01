package com.intellij.settingsSync.jba

import com.intellij.ide.RegionUrlMapper
import com.intellij.ide.plugins.PluginManagerCore.isRunningFromSources
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.settingsSync.core.*
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService
import com.intellij.settingsSync.jba.auth.JBAAuthService
import com.intellij.util.net.PlatformHttpClient
import com.jetbrains.cloudconfig.CloudConfigFileClientV2
import com.jetbrains.cloudconfig.Configuration
import com.jetbrains.cloudconfig.ETagStorage
import com.jetbrains.cloudconfig.FileVersionInfo
import com.jetbrains.cloudconfig.auth.JbaJwtTokenAuthProvider
import com.jetbrains.cloudconfig.exception.UnauthorizedException
import org.jetbrains.annotations.VisibleForTesting
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicReference

internal open class CloudConfigServerCommunicator(private val serverUrl: String?, private val jbaAuthService: JBAAuthService)
  : AbstractServerCommunicator()
{
  private val clientVersionContext = CloudConfigVersionContext()

  @VisibleForTesting
  @Volatile
  internal var currentIdTokenVar: String? = null

  private val clientRef = AtomicReference<CloudConfigFileClientV2>()

  internal open val client: CloudConfigFileClientV2
    get() {
      if (clientRef.get() == null) {
        // can potentially create more than one instance, but it's okay
        clientRef.set(createCloudConfigClient(serverUrl ?: defaultUrl, clientVersionContext))
      }
      return clientRef.get() ?: throw IOException("Communicator is not ready yet")
    }

  private val lastRemoteErrorRef = AtomicReference<Throwable>()

  init {
    SettingsSyncEvents.getInstance().addListener(
      object : SettingsSyncEventListener {
        override fun loginStateChanged() {
          clientRef.set(null)
        }
      }
    )
  }

  @Throws(IOException::class)
  override fun readFileInternal(filePath: String): Pair<InputStream?, String?> {
    return clientVersionContext.doWithVersion(filePath, null) { filePath ->
      try {
        val stream = client.read(filePath)

        val actualVersion: String? = clientVersionContext.get(filePath)
        if (actualVersion == null) {
          LOG.warn("Version not stored in the context for ${filePath} [r]")
        }

        Pair(stream, actualVersion)
      }
      catch (_: FileNotFoundException) {
        Pair(null, null)
      }
    }
  }

  override fun requestSuccessful(){
    if (lastRemoteErrorRef.get() != null) {
      LOG.info("Connection to setting sync server is restored")
    }
    lastRemoteErrorRef.set(null)
  }

  override fun handleRemoteError(e: Throwable): String {
    val defaultMessage = "Error during communication with server"
    if (e is IOException) {
      if (lastRemoteErrorRef.get()?.message != e.message) {
        lastRemoteErrorRef.set(e)
        LOG.warn("$defaultMessage: ${e.message}")
      }
    }
    else if (e is UnauthorizedException) {
      currentIdTokenVar?.also {
        LOG.warn("Got \"Unauthorized\" from Settings Sync server. Settings Sync will be disabled. Please login to JBA again")
        setAuthActionRequired()
        jbaAuthService.invalidateJBA(it)
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

  override fun getLatestVersion(filePath: String): String? = client.getLatestVersion(filePath)?.versionId

  override fun deleteFileInternal(filePath: String) {
    client.delete(filePath)
  }

  override val userId: String
    get() = "jba"

  override fun writeFileInternal(filePath: String, versionId: String?, content: InputStream) : String? {
    return clientVersionContext.doWithVersion(filePath, versionId) { filePath ->
      client.write(filePath, content)

      val actualVersion: String? = clientVersionContext.get(filePath)
      if (actualVersion == null) {
        LOG.warn("Version not stored in the context for ${filePath} [w]")
      }
      actualVersion
    }
  }

  @Throws(Exception::class)
  fun fetchHistory(filePath: String): List<FileVersionInfo> = client.getVersions(filePath)

  @VisibleForTesting
  internal open fun createCloudConfigClient(url: String, versionContext: CloudConfigVersionContext): CloudConfigFileClientV2? {
    val conf = createConfiguration() ?: return null
    return CloudConfigFileClientV2(url, conf, DUMMY_E_TAG_STORAGE, versionContext)
  }

  private fun createConfiguration(): Configuration? {
    val configuration = Configuration().connectTimeout(CONNECTION_TIMEOUT_MS).readTimeout(READ_TIMEOUT_MS)
    val idToken = jbaAuthService.idToken
    currentIdTokenVar = idToken
    if (idToken == null) {
      if (jbaAuthService.getAccountInfoService()?.userData != null) {
        setAuthActionRequired()
      }
      return null
    }
    else {
      configuration.auth(JbaJwtTokenAuthProvider(idToken))
    }
    return configuration
  }

  private fun setAuthActionRequired() {
    @Suppress("DEPRECATION")
    if (SettingsSyncStatusTracker.getInstance().currentStatus is SettingsSyncStatusTracker.SyncStatus.ActionRequired) return

    jbaAuthService.authRequiredAction = SettingsSyncAuthService.PendingUserAction(
      message = SettingsSyncJbaBundle.message("action.settingsSync.authRequired"),
      actionTitle = SettingsSyncBundle.message("config.button.login"),
      actionDescription = SettingsSyncJbaBundle.message("action.settingsSync.authRequired.text")
    ) {
      val userData = jbaAuthService.login(it)
      if (userData != null) {
        jbaAuthService.authRequiredAction = null
        SettingsSyncStatusTracker.getInstance().updateOnSuccess()
      }
    }
  }

  override fun dispose() {
    if (LOG.isDebugEnabled) {
      LOG.debug("Disposing...")
    }
    clientRef.set(null)
    currentIdTokenVar = null
  }

  companion object {
    private const val CONNECTION_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 50000

    private const val URL_PROVIDER = "https://www.jetbrains.com/config/IdeaCloudConfig.xml"
    private const val DEFAULT_PRODUCTION_URL = "https://cloudconfig.jetbrains.com/cloudconfig"
    private const val DEFAULT_DEBUG_URL = "https://stgn.cloudconfig.jetbrains.com/cloudconfig"
    private const val URL_PROPERTY = "idea.settings.sync.cloud.url"

    internal val defaultUrl: String by lazy {
      val explicitUrl = System.getProperty(URL_PROPERTY)
      if (explicitUrl != null) {
        LOG.info("Using SettingSync server URL (from properties): ${explicitUrl}")
        return@lazy explicitUrl
      }

      if (isRunningFromSources()) {
        LOG.info("Using SettingSync server URL (DEBUG): ${DEFAULT_DEBUG_URL}")
        return@lazy DEFAULT_DEBUG_URL
      }

      try {
        val regionalUrl = RegionUrlMapper.tryMapUrlBlocking(URL_PROVIDER)
        val request = PlatformHttpClient.request(URI(regionalUrl))
        val response = PlatformHttpClient.checkResponse(PlatformHttpClient.client().send(request, HttpResponse.BodyHandlers.ofByteArray()))
        val configUrl = JDOMUtil.load(response.body()).getAttributeValue("baseUrl")
        LOG.info("Using SettingSync server URL: ${configUrl}")
        configUrl
      }
      catch (e: Exception) {
        LOG.warn("Failed to obtain a SettingSync server URL", e)
        LOG.info("Using SettingSync server URL (fallback): ${DEFAULT_PRODUCTION_URL}")
        DEFAULT_PRODUCTION_URL
      }
    }

    private val LOG = logger<CloudConfigServerCommunicator>()

    @VisibleForTesting
    internal val DUMMY_E_TAG_STORAGE: ETagStorage = object : ETagStorage {
      override fun get(path: String): String? = null
      override fun store(path: String, value: String) { }
      override fun remove(path: String?) { }
    }
  }
}

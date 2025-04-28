package com.intellij.settingsSync.jba

import com.intellij.ide.plugins.PluginManagerCore.isRunningFromSources
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.settingsSync.core.AbstractServerCommunicator
import com.intellij.settingsSync.core.SettingsSyncBundle
import com.intellij.settingsSync.core.SettingsSyncEventListener
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.SettingsSyncStatusTracker
import com.intellij.settingsSync.jba.auth.JBAAuthService
import com.intellij.util.io.HttpRequests
import com.jetbrains.cloudconfig.CloudConfigFileClientV2
import com.jetbrains.cloudconfig.Configuration
import com.jetbrains.cloudconfig.ETagStorage
import com.jetbrains.cloudconfig.FileVersionInfo
import com.jetbrains.cloudconfig.auth.JbaJwtTokenAuthProvider
import com.jetbrains.cloudconfig.exception.UnauthorizedException
import org.jdom.JDOMException
import org.jetbrains.annotations.VisibleForTesting
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference

private const val CONNECTION_TIMEOUT_MS = 10000
private const val READ_TIMEOUT_MS = 50000

internal open class CloudConfigServerCommunicator(private val serverUrl: String? = null,
  private val jbaAuthService: JBAAuthService
) : AbstractServerCommunicator() {

  private val clientVersionContext = CloudConfigVersionContext()

  @VisibleForTesting
  @Volatile
  internal var _currentIdTokenVar: String? = null

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
          LOG.warn("Version not stored in the context for $filePath")
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
      _currentIdTokenVar?.also {
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

  override fun getLatestVersion(filePath: String): String? {
    return client.getLatestVersion(filePath)?.versionId
  }

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
        LOG.warn("Version not stored in the context for $filePath")
      }
      actualVersion
    }
  }

  @Throws(Exception::class)
  fun fetchHistory(filePath: String): List<FileVersionInfo> {
    return client.getVersions(filePath)
  }

  @VisibleForTesting
  internal open fun createCloudConfigClient(url: String, versionContext: CloudConfigVersionContext): CloudConfigFileClientV2? {
    val conf = createConfiguration() ?: return null
    return CloudConfigFileClientV2(url, conf, DUMMY_ETAG_STORAGE, versionContext)
  }

  private fun createConfiguration(): Configuration? {
    val configuration = Configuration().connectTimeout(CONNECTION_TIMEOUT_MS).readTimeout(READ_TIMEOUT_MS)
    val idToken = jbaAuthService.idToken
    _currentIdTokenVar = idToken
    if (idToken == null) {
      if (jbaAuthService.getAccountInfoService()?.userData != null) {
        setAuthActionRequired()
      }
      return null
    } else {
      configuration.auth(JbaJwtTokenAuthProvider(idToken))
    }
    return configuration
  }

  private fun setAuthActionRequired() {
    if (SettingsSyncStatusTracker.getInstance().currentStatus is SettingsSyncStatusTracker.SyncStatus.ActionRequired)
      return
    SettingsSyncStatusTracker.getInstance().setActionRequired(
      SettingsSyncJbaBundle.message("action.settingsSync.authRequired"),
      SettingsSyncBundle.message("config.button.login")) {
      val userData = jbaAuthService.login(it)
      if (userData != null) {
        SettingsSyncStatusTracker.getInstance().clearActionRequired()
        SettingsSyncStatusTracker.getInstance().updateOnSuccess()
      }
    }
  }

  override fun dispose() {
    if (LOG.isDebugEnabled) {
      LOG.debug("Disposing...")
    }
    clientRef.set(null)
    _currentIdTokenVar = null
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
        .connect({ request: HttpRequests.Request ->
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

internal fun enabledOrDisabled(value: Boolean?): String {
  return if (value == null) "null" else if (value) "enabled" else "disabled"
}
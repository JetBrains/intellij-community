package circlet.components

import circlet.arenas.initCircletArenas
import circlet.auth.startRedirectHandling
import circlet.client.api.impl.ApiClassesDeserializer
import circlet.client.api.impl.tombstones.registerArenaTombstones
import circlet.common.oauth.IdeaOAuthConfig
import circlet.permission.FeatureFlagsVmPersistenceKey
import circlet.platform.api.oauth.OAuthTokenResponse
import circlet.platform.api.oauth.toTokenInfo
import circlet.platform.api.serialization.ExtendableSerializationRegistry
import circlet.platform.workspaces.CodeFlowConfig
import circlet.platform.workspaces.WorkspaceConfiguration
import circlet.platform.workspaces.WorkspaceManagerHost
import circlet.runtime.ApplicationDispatcher
import circlet.settings.CircletServerSettings
import circlet.settings.CircletSettings
import circlet.settings.CircletSettingsPanel
import circlet.utils.IdeaPasswordSafePersistence
import circlet.utils.LifetimedDisposable
import circlet.utils.LifetimedDisposableImpl
import circlet.utils.notify
import circlet.workspaces.Workspace
import circlet.workspaces.WorkspaceManager
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import libraries.coroutines.extra.withContext
import libraries.klogging.KLogger
import libraries.klogging.assert
import libraries.klogging.logger
import runtime.Ui
import runtime.mutableUiDispatch
import runtime.persistence.InMemoryPersistence
import runtime.persistence.PersistenceConfiguration
import runtime.persistence.PersistenceKey
import runtime.reactive.SequentialLifetimes
import runtime.reactive.flatMapInit
import runtime.reactive.mutableProperty
import java.net.URI
import java.net.URL

// monitors CircletConfigurable state, creates and exposed instance of Workspace, provides various state properties and callbacks.
class CircletWorkspaceComponent : WorkspaceManagerHost(), LifetimedDisposable by LifetimedDisposableImpl() {
  private val log: KLogger = logger<CircletWorkspaceComponent>()

  private val ideaClientPersistenceConfiguration = PersistenceConfiguration(
    FeatureFlagsVmPersistenceKey,
    PersistenceKey.Arena
  )

  private val workspacesLifetimes = SequentialLifetimes(lifetime)

  private val manager = mutableProperty<WorkspaceManager?>(null)

  val workspace = flatMapInit<WorkspaceManager?, Workspace?>(manager, null) {
    (it?.workspace ?: mutableProperty<Workspace?>(null))
  }

  private val settings = CircletSettings.getInstance()

  init {
    initApp()
    val settingsOnStartup = settings.serverSettings
    val wsLifetime = workspacesLifetimes.next()

    // sign in automatically on application startup.
    launch(wsLifetime, Ui) {
      if (!autoSignIn(settingsOnStartup, wsLifetime)) {
        notifyDisconnected()
      }
    }
  }

  private fun initApp() {
    val application = ApplicationManager.getApplication()

    mutableUiDispatch = ApplicationDispatcher(application)

    initCircletArenas()
    registerArenaTombstones(ExtendableSerializationRegistry.global)

    ApiClassesDeserializer(ExtendableSerializationRegistry.global).registerDeserializers()
  }


  override suspend fun authFailed() {
    authCheckFailedNotification()
    manager.value?.signOut(false)
  }

  suspend fun signIn(lifetime: Lifetime, server: String): OAuthTokenResponse {
    log.assert(manager.value == null, "manager.value == null")

    val lt = workspacesLifetimes.next()
    val wsConfig = ideaConfig(server)
    val wss = WorkspaceManager(lt, null, this, InMemoryPersistence(), IdeaPasswordSafePersistence, ideaClientPersistenceConfiguration,
                               wsConfig)

    val portsMapping: Map<Int, URL> = IdeaOAuthConfig.redirectURIs.map { rawUri -> URL(rawUri).let { url -> url.port to url } }.toMap()
    val ports = portsMapping.keys
    val (port, redirectUrl) = startRedirectHandling(lifetime, ports)
                              ?: return OAuthTokenResponse.Error(wsConfig.server, "", "The ports required for authorization are busy")

    val authUrl = portsMapping.getValue(port)
    val codeFlow = CodeFlowConfig(wsConfig, authUrl.toExternalForm())
    val uri = URI(codeFlow.codeFlowURL())
    try {
      BrowserLauncher.instance.browse(uri)
    }
    catch (th: Throwable) {
      return OAuthTokenResponse.Error(wsConfig.server, "", "Can't open '${wsConfig.server}' in system browser.")
    }

    val response = withContext(lifetime, AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()) {
      codeFlow.handleCodeFlowRedirect(redirectUrl.await())
    }
    if (response is OAuthTokenResponse.Success) {
      log.info { "A personal token was received" }
      wss.signInWithToken(response.toTokenInfo())
      settings.serverSettings = CircletServerSettings(true, server)
      manager.value = wss
      notifyConnected()
    }
    return response
  }

  fun signOut() {
    val oldManager = manager.value
    oldManager?.signOut(true)
    workspacesLifetimes.clear()
    manager.value = null
    settings.serverSettings = settings.serverSettings.copy(enabled = false)
  }

  private suspend fun autoSignIn(settingsOnStartup: CircletServerSettings, wsLifetime: Lifetime): Boolean {
    if (settingsOnStartup.server.isNotBlank() && settingsOnStartup.enabled) {
      val wsConfig = ideaConfig(settingsOnStartup.server)
      val wss = WorkspaceManager(wsLifetime, null, this@CircletWorkspaceComponent, InMemoryPersistence(), IdeaPasswordSafePersistence,
                                 ideaClientPersistenceConfiguration, wsConfig)
      if (wss.signInNonInteractive()) {
        manager.value = wss
        return true
      }
    }
    return false
  }

}

val circletWorkspace: CircletWorkspaceComponent
  get() = ServiceManager.getService(CircletWorkspaceComponent::class.java)


private fun notifyDisconnected() {
  notify("Disconnected.<br><a href=\"switch-on\">Configure Server</a>", ::configure)
}

private fun notifyConnected() {
  notify("Connected")
}

private fun authCheckFailedNotification() {
  notify("Not authenticated.<br> <a href=\"sign-in\">Sign in</a>") {
  }
}

private fun configure() {
  CircletSettingsPanel.openSettings(null)
}

fun ideaConfig(server: String): WorkspaceConfiguration {
  return WorkspaceConfiguration(
    server,
    IdeaOAuthConfig.clientId,
    IdeaOAuthConfig.clientSecret)
}


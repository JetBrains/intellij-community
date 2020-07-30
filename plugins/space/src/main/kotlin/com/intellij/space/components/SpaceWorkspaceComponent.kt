package com.intellij.space.components

import circlet.arenas.initCircletArenas
import com.intellij.space.auth.SpaceAuthNotifier
import com.intellij.space.auth.startRedirectHandling
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
import com.intellij.space.runtime.ApplicationDispatcher
import com.intellij.space.settings.SpaceServerSettings
import com.intellij.space.settings.SpaceSettings
import com.intellij.space.utils.IdeaPasswordSafePersistence
import com.intellij.space.utils.LifetimedDisposable
import com.intellij.space.utils.LifetimedDisposableImpl
import circlet.workspaces.Workspace
import circlet.workspaces.WorkspaceManager
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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

internal val space: SpaceWorkspaceComponent
  get() = service()

/**
 * The main plugin's component that allows to log in to the Space server or disconnect from it.
 * If possible, the component is automatically authorized when the app starts
 */
@Service
internal class SpaceWorkspaceComponent : WorkspaceManagerHost(), LifetimedDisposable by LifetimedDisposableImpl() {
  private val workspacesLifetimes = SequentialLifetimes(lifetime)

  private val manager = mutableProperty<WorkspaceManager?>(null)

  val workspace = flatMapInit<WorkspaceManager?, Workspace?>(manager, null) {
    (it?.workspace ?: mutableProperty<Workspace?>(null))
  }

  private val settings = SpaceSettings.getInstance()

  init {
    initApp()
    val wsLifetime = workspacesLifetimes.next()

    // sign in automatically on application startup.
    launch(wsLifetime, Ui) {
      if (!autoSignIn(wsLifetime)) {
        SpaceAuthNotifier.notifyDisconnected()
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
    SpaceAuthNotifier.authCheckFailedNotification()
    manager.value?.signOut(false)
  }

  suspend fun signIn(lifetime: Lifetime, server: String): OAuthTokenResponse {
    LOG.assert(manager.value == null, "manager.value == null")

    val lt = workspacesLifetimes.next()
    val newManager = createWorkspaceManager(lt, server)

    val portsMapping: Map<Int, URL> = IdeaOAuthConfig.redirectURIs.map { rawUri -> URL(rawUri).let { url -> url.port to url } }.toMap()
    val ports = portsMapping.keys
    val (port, redirectUrl) = startRedirectHandling(lifetime, ports)
                              ?: return OAuthTokenResponse.Error(server, "", "The ports required for authorization are busy")

    val authUrl = portsMapping.getValue(port)
    val codeFlow = CodeFlowConfig(newManager.wsConfig, authUrl.toExternalForm())
    val uri = URI(codeFlow.codeFlowURL())
    try {
      BrowserLauncher.instance.browse(uri)
    }
    catch (th: Throwable) {
      return OAuthTokenResponse.Error(server, "", "Can't open '$server' in system browser.")
    }

    val response = withContext(lifetime, AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()) {
      codeFlow.handleCodeFlowRedirect(redirectUrl.await())
    }
    if (response is OAuthTokenResponse.Success) {
      LOG.info { "A personal token was received" }
      newManager.signInWithToken(response.toTokenInfo())
      settings.serverSettings = SpaceServerSettings(true, server)
      manager.value = newManager
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

  private suspend fun autoSignIn(wsLifetime: Lifetime): Boolean {
    val serverSettings = SpaceSettings.getInstance().serverSettings
    val server = serverSettings.server
    if (serverSettings.enabled && server.isNotBlank()) {
      val newManager = createWorkspaceManager(wsLifetime, server)
      if (newManager.signInNonInteractive()) {
        manager.value = newManager
        return true
      }
    }
    return false
  }

  private fun createWorkspaceManager(lifetime: Lifetime, server: String): WorkspaceManager {
    val persistenceConfig = PersistenceConfiguration(
      FeatureFlagsVmPersistenceKey,
      PersistenceKey.Arena
    )
    val workspaceConfig = WorkspaceConfiguration(server, IdeaOAuthConfig.clientId, IdeaOAuthConfig.clientSecret)
    return WorkspaceManager(lifetime, null, this, InMemoryPersistence(), IdeaPasswordSafePersistence, persistenceConfig, workspaceConfig)
  }

  companion object {
    private val LOG: KLogger = logger<SpaceWorkspaceComponent>()
  }
}


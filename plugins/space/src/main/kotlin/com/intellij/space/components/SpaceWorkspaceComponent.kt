package com.intellij.space.components

import circlet.arenas.initCircletArenas
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
import circlet.workspaces.Workspace
import circlet.workspaces.WorkspaceManager
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.IdeFrame
import com.intellij.space.auth.SpaceAuthNotifier
import com.intellij.space.auth.startRedirectHandling
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.runtime.ApplicationDispatcher
import com.intellij.space.settings.SpaceLoginState
import com.intellij.space.settings.SpaceServerSettings
import com.intellij.space.settings.SpaceSettings
import com.intellij.space.settings.log
import com.intellij.space.utils.IdeaPasswordSafePersistence
import com.intellij.space.utils.LifetimedDisposable
import com.intellij.space.utils.LifetimedDisposableImpl
import com.intellij.ui.AppIcon
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import libraries.coroutines.extra.usingSource
import libraries.coroutines.extra.withContext
import libraries.klogging.KLogger
import libraries.klogging.assert
import libraries.klogging.logger
import runtime.Ui
import runtime.mutableUiDispatch
import runtime.persistence.InMemoryPersistence
import runtime.persistence.PersistenceConfiguration
import runtime.persistence.PersistenceKey
import runtime.reactive.*
import java.awt.Component
import java.net.URI
import java.net.URL
import java.util.concurrent.CancellationException
import javax.swing.JFrame
import javax.swing.SwingUtilities

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

  val workspace: Property<Workspace?> = map(manager) { wm ->
    wm?.workspace?.value
  }

  private val settings = SpaceSettings.getInstance()

  val loginState: MutableProperty<SpaceLoginState> = mutableProperty(getInitialState())

  init {
    initApp()
    val wsLifetime = workspacesLifetimes.next()

    // sign in automatically on application startup.
    launch(wsLifetime, Ui) {
      if (!autoSignIn(wsLifetime)) {
        SpaceAuthNotifier.notifyDisconnected()
      }
    }

    workspace.forEach(lifetime) { ws ->
      System.setProperty("space_server_for_script_definition", ws?.client?.server?.let { "$it/system/maven" } ?: "not_set")

      loginState.value = if (ws == null) {
        SpaceLoginState.Disconnected(settings.serverSettings.server)
      }
      else {
        SpaceLoginState.Connected(ws.client.server, ws)
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
                              ?: return OAuthTokenResponse.Error(server, "", SpaceBundle.message("auth.error.ports.busy.label"))

    val authUrl = portsMapping.getValue(port)
    val codeFlow = CodeFlowConfig(newManager.wsConfig, authUrl.toExternalForm())
    val uri = URI(codeFlow.codeFlowURL())
    try {
      BrowserLauncher.instance.browse(uri)
    }
    catch (th: Throwable) {
      return OAuthTokenResponse.Error(server, "", SpaceBundle.message("auth.error.cant.open.browser.label", server))
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

  fun signInManually(serverName: String, uiLifetime: Lifetime, component: Component) {
    launch(uiLifetime, Ui) {
      uiLifetime.usingSource { connectLt ->
        try {
          loginState.value = SpaceLoginState.Connecting(serverName) {
            connectLt.terminate()
            loginState.value = SpaceLoginState.Disconnected(serverName)
          }
          when (val response = space.signIn(connectLt, serverName)) {
            is OAuthTokenResponse.Error -> {
              loginState.value = SpaceLoginState.Disconnected(serverName, response.description)
            }
          }
        }
        catch (th: CancellationException) {
          throw th
        }
        catch (th: Throwable) {
          log.error(th)
          loginState.value = SpaceLoginState.Disconnected(
            serverName,
            th.message ?: SpaceBundle.message("auth.error.unknown.label", th.javaClass.simpleName)
          )
        }
        val frame = SwingUtilities.getAncestorOfClass(JFrame::class.java, component)
        AppIcon.getInstance().requestFocus(frame as IdeFrame?)
      }
    }
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

  private fun getInitialState(): SpaceLoginState {
    val workspace = workspace.value ?: return SpaceLoginState.Disconnected("")
    return SpaceLoginState.Connected(workspace.client.server, workspace)
  }

  companion object {
    private val LOG: KLogger = logger<SpaceWorkspaceComponent>()
  }
}


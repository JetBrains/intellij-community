// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.components

import circlet.arenas.initCircletArenas
import circlet.client.api.impl.ApiClassesDeserializer
import circlet.client.api.impl.tombstones.registerArenaTombstones
import circlet.code.api.CodeReviewArena
import circlet.code.api.CodeReviewParticipantsArena
import circlet.code.api.ReviewPendingMessageCounterArena
import circlet.common.oauth.IdeaOAuthConfig
import circlet.permissions.FeatureFlagsVmPersistenceKey
import circlet.platform.api.oauth.OAuthTokenResponse
import circlet.platform.api.oauth.toTokenInfo
import circlet.platform.api.serialization.ExtendableSerializationRegistry
import circlet.platform.client.ClientArenaRegistry
import circlet.platform.workspaces.CodeFlowConfig
import circlet.platform.workspaces.WorkspaceConfiguration
import circlet.platform.workspaces.WorkspaceManagerHost
import circlet.workspaces.Workspace
import circlet.workspaces.WorkspaceManager
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
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
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.utils.IdeaPasswordSafePersistence
import com.intellij.space.utils.LifetimedDisposable
import com.intellij.space.utils.LifetimedDisposableImpl
import com.intellij.ui.AppIcon
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import libraries.coroutines.extra.usingSource
import libraries.klogging.KLogger
import libraries.klogging.assert
import libraries.klogging.logger
import runtime.Ui
import runtime.mutableUiDispatch
import runtime.persistence.InMemoryPersistence
import runtime.persistence.PersistenceConfiguration
import runtime.persistence.PersistenceKey
import runtime.reactive.MutableProperty
import runtime.reactive.Property
import runtime.reactive.SequentialLifetimes
import runtime.reactive.mutableProperty
import runtime.reactive.property.map
import java.awt.Component
import java.net.URI
import java.net.URL
import java.util.concurrent.CancellationException
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * The main plugin's component that allows to log in to the Space server or disconnect from it.
 * If possible, the component is automatically authorized when the app starts
 */
@Service(Level.APP)
internal class SpaceWorkspaceComponent : WorkspaceManagerHost(), LifetimedDisposable by LifetimedDisposableImpl() {
  companion object {
    internal fun getInstance(): SpaceWorkspaceComponent = service()
    private val LOG: KLogger = logger<SpaceWorkspaceComponent>()
  }

  private val workspacesLifetimes = SequentialLifetimes(lifetime)

  private val manager = mutableProperty<WorkspaceManager?>(null)

  val workspace: Property<Workspace?> = map(manager) { wm ->
    wm?.workspace?.value
  }

  val loginState: MutableProperty<SpaceLoginState> = mutableProperty(getInitialState())

  init {
    initApp()
    val wsLifetime = workspacesLifetimes.next()

    // sign in automatically on application startup.
    launch(wsLifetime, Ui) {
      val signInResult = autoSignIn(wsLifetime)
      if (signInResult == AutoSignInResult.NOT_AUTHORIZED) {
        authFailed()
      }
    }

    workspace.forEach(lifetime) { ws ->
      loginState.value = if (ws == null) {
        SpaceLoginState.Disconnected(SpaceSettings.getInstance().serverSettings.server)
      }
      else {
        SpaceLoginState.Connected(ws.client.server, ws)
      }
    }
  }

  private fun initApp() {
    val application = ApplicationManager.getApplication()

    mutableUiDispatch = ApplicationDispatcher(this, application)

    initCircletArenas()
    registerArenaTombstones(ExtendableSerializationRegistry.global)

    ApiClassesDeserializer(ExtendableSerializationRegistry.global).registerDeserializers()

    // code review
    ClientArenaRegistry.register(CodeReviewArena)
    ClientArenaRegistry.register(CodeReviewParticipantsArena)
    ClientArenaRegistry.register(ReviewPendingMessageCounterArena)
    circlet.code.api.impl.ApiClassesDeserializer(ExtendableSerializationRegistry.global).registerDeserializers()
  }


  override suspend fun authFailed() {
    signOut(SpaceStatsCounterCollector.LogoutPlace.AUTH_FAIL)
    SpaceAuthNotifier.authFailed()
  }

  private suspend fun signIn(lifetime: Lifetime, server: String): OAuthTokenResponse {
    LOG.assert(manager.value == null, "manager.value == null")

    val lt = workspacesLifetimes.next()
    val newManager = createWorkspaceManager(lt, server)

    val portsMapping: Map<Int, URL> = IdeaOAuthConfig.redirectURIs.map { rawUri -> URL(rawUri).let { url -> url.port to url } }.toMap()
    val ports = portsMapping.keys
    val (port, redirectUrl) = startRedirectHandling(lifetime, server, ports)
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

    val response = withContext(AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()) {
      codeFlow.handleCodeFlowRedirect(redirectUrl.await())
    }
    if (response is OAuthTokenResponse.Success) {
      LOG.info { "A personal token was received" }
      newManager.signInWithToken(response.toTokenInfo())
      SpaceSettings.getInstance().serverSettings = SpaceServerSettings(true, server)
      manager.value = newManager
    }
    return response
  }

  fun signInManually(server:String, uiLifetime: Lifetime, component: Component) {
    var serverName = server
    if (serverName.isBlank()) {
      val error = SpaceBundle.message("login.panel.error.organization.url.should.not.be.empty")
      loginState.value = SpaceLoginState.Disconnected("", error)
      return
    }
    serverName = normalizeUrl(serverName)
    launch(uiLifetime, Ui) {
      uiLifetime.usingSource { connectLt ->
        try {
          loginState.value = SpaceLoginState.Connecting(serverName) {
            connectLt.terminate()
            loginState.value = SpaceLoginState.Disconnected(serverName)
          }
          when (val response = signIn(connectLt, serverName)) {
            is OAuthTokenResponse.Error -> {
              val error = response.description // NON-NLS
              loginState.value = SpaceLoginState.Disconnected(serverName, error)
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

  private fun normalizeUrl(serverName: String): String {
    var result = serverName
    result = if (result.startsWith("https://") || result.startsWith("http://")) result else "https://$result"
    result = result.removeSuffix("/")
    return result
  }

  fun signOut(statsPlace: SpaceStatsCounterCollector.LogoutPlace) {
    SpaceStatsCounterCollector.LOG_OUT.log(statsPlace)
    val oldManager = manager.value
    oldManager?.signOut(true)
    workspacesLifetimes.clear()
    manager.value = null
    val settings = SpaceSettings.getInstance()
    settings.serverSettings = settings.serverSettings.copy(enabled = false)
  }

  private suspend fun autoSignIn(wsLifetime: Lifetime): AutoSignInResult {
    val serverSettings = SpaceSettings.getInstance().serverSettings
    val server = serverSettings.server
    if (!serverSettings.enabled || server.isBlank()) {
      return AutoSignInResult.NOT_AUTHORIZED_BEFORE
    }
    val newManager = createWorkspaceManager(wsLifetime, server)
    return try {
      if (newManager.signInNonInteractive()) {
        manager.value = newManager
        AutoSignInResult.AUTHORIZED
      }
      else {
        AutoSignInResult.NOT_AUTHORIZED
      }
    }
    catch (th: Throwable) {
      LOG.info(th, "Couldn't authorize interactively")
      AutoSignInResult.NOT_AUTHORIZED
    }
  }

  private fun createWorkspaceManager(lifetime: Lifetime, server: String): WorkspaceManager {
    val persistenceConfig = PersistenceConfiguration(
      FeatureFlagsVmPersistenceKey,
      PersistenceKey.AllArenas
    )
    val workspaceConfig = WorkspaceConfiguration(server, IdeaOAuthConfig.clientId, IdeaOAuthConfig.clientSecret)
    return WorkspaceManager(lifetime, null, this, InMemoryPersistence(), IdeaPasswordSafePersistence, persistenceConfig, workspaceConfig)
  }

  private fun getInitialState(): SpaceLoginState {
    val workspace = workspace.value ?: return SpaceLoginState.Disconnected("")
    return SpaceLoginState.Connected(workspace.client.server, workspace)
  }

  private enum class AutoSignInResult {
    NOT_AUTHORIZED_BEFORE,
    NOT_AUTHORIZED,
    AUTHORIZED
  }
}


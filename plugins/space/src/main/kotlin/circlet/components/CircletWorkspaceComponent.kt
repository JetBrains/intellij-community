package circlet.components

import circlet.arenas.initCircletArenas
import circlet.auth.accessTokenInteractive
import circlet.client.api.impl.ApiClassesDeserializer
import circlet.common.oauth.IdeaOAuthConfig
import circlet.permission.FeatureFlagsVmPersistenceKey
import circlet.platform.api.oauth.OAuthTokenResponse
import circlet.platform.api.oauth.toTokenInfo
import circlet.platform.api.serialization.ExtendableSerializationRegistry
import circlet.platform.client.ConnectionStatus
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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import libraries.klogging.assert
import libraries.klogging.logger
import runtime.Ui
import runtime.mutableUiDispatch
import runtime.persistence.InMemoryPersistence
import runtime.persistence.PersistenceConfiguration
import runtime.persistence.PersistenceKey
import runtime.reactive.*

private val log = logger<CircletWorkspaceComponent>()

// monitors CircletConfigurable state, creates and exposed instance of Workspace, provides various state properties and callbacks.
class CircletWorkspaceComponent : WorkspaceManagerHost(), LifetimedDisposable by LifetimedDisposableImpl() {

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
            }
            notifyDisconnected(wsLifetime)
        }

        // notify circlet is connected on the first connect (remove it later)
        workspace.whenNotNull(lifetime) { lt, ws ->
            ws.client.connectionStatus.filterIsInstance<ConnectionStatus.Connected>().first().forEach(lt) { status ->
                notifyConnected(lt)
            }
        }
    }

    private fun initApp() {
        val application = ApplicationManager.getApplication()
        //if (!application.isUnitTestMode && !application.isHeadlessEnvironment) {
        //    KLoggerStaticFactory.customFactory = KLoggerFactoryIdea
        //}

        mutableUiDispatch = ApplicationDispatcher(application)

        initCircletArenas()

        ApiClassesDeserializer(ExtendableSerializationRegistry.global).registerDeserializers()
    }


    override suspend fun authFailed() {
        authCheckFailedNotification(lifetime)
        manager.value?.signOut(false)
    }

    suspend fun signIn(lifetime: Lifetime, server: String): OAuthTokenResponse {
        log.assert(manager.value == null, "manager.value == null")

        val lt = workspacesLifetimes.next()
        val wsConfig = ideaConfig(server)
        val wss = WorkspaceManager(lt, null, this, InMemoryPersistence(), IdeaPasswordSafePersistence, ideaClientPersistenceConfiguration, wsConfig)
        val response = accessTokenInteractive(lifetime, wsConfig)
        if (response is OAuthTokenResponse.Success) {
            log.info { "response = ${response.accessToken} ${response.expiresIn} ${response.refreshToken} ${response.scope}" }
            wss.signInWithToken(response.toTokenInfo())
            settings.serverSettings = CircletServerSettings(true, server)
            manager.value = wss
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
            val wss = WorkspaceManager(wsLifetime, null, this@CircletWorkspaceComponent, InMemoryPersistence(), IdeaPasswordSafePersistence, ideaClientPersistenceConfiguration, wsConfig)
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


private fun notifyDisconnected(lifetime: Lifetime) {
    notify(lifetime, "Disconnected.<br><a href=\"switch-on\">Configure Server</a>", ::configure)
}

private fun notifyConnected(lifetime: Lifetime) {
    notify(lifetime, "Connected")
}

private fun notifyAuthFailed(lifetime: Lifetime) {
    notify(lifetime, "Auth Failed")
}

private fun authCheckFailedNotification(lifetime: Lifetime) {
    notify(lifetime, "Not authenticated.<br> <a href=\"sign-in\">Sign in</a>", {
    })
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


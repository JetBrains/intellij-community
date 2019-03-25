package circlet.components

import circlet.auth.*
import circlet.common.oauth.*
import circlet.platform.api.oauth.*
import circlet.platform.client.*
import circlet.settings.*
import circlet.utils.*
import circlet.workspaces.*
import com.intellij.openapi.components.*
import com.intellij.openapi.options.*
import klogging.*
import runtime.*
import runtime.async.*
import runtime.klogger.*
import runtime.persistence.*
import runtime.reactive.*

val circletWorkspace get() = application.getComponent<CircletWorkspaceComponent>()

private val log = logger<CircletWorkspaceComponent>()

// monitors CircletConfigurable state, creates and exposed instance of Workspace, provides various state properties and callbacks.
class CircletWorkspaceComponent : ApplicationComponent, WorkspaceManagerHost(), LifetimedComponent by SimpleLifetimedComponent() {

    private val ideaClintPersistenceConfiguration = PersistenceConfiguration()

    private val workspacesLifetimes = SequentialLifetimes(lifetime)
    private val manager = mutableProperty<WorkspaceManager?>(null)

    val workspace = flatMap<WorkspaceManager?, Workspace?>(manager, null) {
        (it?.workspace ?: mutableProperty<Workspace?>(null))
    }

    override fun initComponent() {
        val settingsOnStartup = circletSettings.settings.value
        val wsLifetime = workspacesLifetimes.next()

        // sign in automatically on application startup.
        launch(wsLifetime, Ui) {
            if (!autoSignIn(settingsOnStartup, wsLifetime))
                notifyDisconnected(wsLifetime)
        }

        // notify circlet is connected on the first connect (remove it later)
        workspace.whenNotNull(lifetime) { lt, ws ->
            ws.client.connectionStatus.filterIsInstance<ConnectionStatus.Connected>().first().forEach(lt) { status ->
                notifyConnected(lt)
            }
        }
    }

    override suspend fun authFailed() {
        authCheckFailedNotification(lifetime)
        manager.value?.signOut(false)
    }

    suspend fun signIn(lifetime: Lifetime, server: String): OAuthTokenResponse {
        log.assert(manager.value == null, "manager.value == null")

        val lt = workspacesLifetimes.next()
        val wsConfig = ideaConfig(server)
        val wss = WorkspaceManager(lt, this, InMemoryPersistence(), IdeaPasswordSafePersistence, ideaClintPersistenceConfiguration, wsConfig)
        val response = accessTokenInteractive(lifetime, wsConfig)
        if (response is OAuthTokenResponse.Success) {
            log.info { "response = ${response.accessToken} ${response.expiresIn} ${response.refreshToken} ${response.scope}" }
            wss.signInWithToken(response.toTokenInfo())
            circletSettings.applySettings(CircletServerSettings(true, server))
            manager.value = wss
        }
        return response
    }

    fun signOut() {
        val oldManager = manager.value
        oldManager?.signOut(true)
        workspacesLifetimes.clear()
        manager.value = null
        circletSettings.applySettings(circletSettings.state.copy(enabled = false))
    }

    private suspend fun autoSignIn(settingsOnStartup: CircletServerSettings, wsLifetime: Lifetime): Boolean {
        if (settingsOnStartup.server.isNotBlank() && settingsOnStartup.enabled) {
            val wsConfig = ideaConfig(settingsOnStartup.server)
            val wss = WorkspaceManager(wsLifetime, this@CircletWorkspaceComponent, InMemoryPersistence(), IdeaPasswordSafePersistence, ideaClintPersistenceConfiguration, wsConfig)
            if (wss.signInNonInteractive()) {
                manager.value = wss
                return true
            }
        }
        return false
    }

}

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
    ShowSettingsUtil.getInstance().showSettingsDialog(null, CircletConfigurable::class.java)
}

fun ideaConfig(server: String): WorkspaceConfiguration {
    return WorkspaceConfiguration(
        server,
        IdeaOAuthConfig.clientId,
        IdeaOAuthConfig.clientSecret)
}


package circlet.components

import circlet.auth.*
import circlet.common.oauth.*
import circlet.platform.client.*
import circlet.settings.*
import circlet.utils.*
import circlet.workspaces.*
import com.intellij.openapi.components.*
import com.intellij.openapi.options.*
import runtime.*
import runtime.async.*
import runtime.reactive.*

val circletWorkspace get() = application.getComponent<CircletWorkspaceComponent>()

// monitors CircletConfigurable state, creates and exposed instance of Workspace, provides various state properties and callbacks.
class CircletWorkspaceComponent : ApplicationComponent, LifetimedComponent by SimpleLifetimedComponent(), WorkspaceHost {

    private val workspacesLifetimes = SequentialLifetimes(lifetime)
    val workspaces = mutableProperty<Workspaces?>(null)

    val workspace = flatMap(workspaces, null) {
        (it?.workspace ?: mutableProperty<Workspace?>(null)) as MutableProperty<Workspace?>
    }

    override suspend fun getTokenInteractive(lifetime: Lifetime, authPersistence: Persistence, wsConfig: WorkspaceConfiguration): TokenInfo {
        val token = IdeaAuthenticator(lifetime, wsConfig).authTokenInteractive()
        return when (token) {
            is OAuthTokenResponse.Success -> {
                token.toTokenInfo()
            }
            is OAuthTokenResponse.Error -> {
                error("no token, todo:")
            }
        }
    }

    override fun initComponent() {
        val settingsOnStartup = circletSettings.settings.value
        launch(lifetime, Ui) {
            val lt = workspacesLifetimes.next()
            if (settingsOnStartup.server.isNotBlank() && settingsOnStartup.enabled) {
                val wsConfig = ideaConfig(settingsOnStartup.server)
                val wss = Workspaces(lt, this@CircletWorkspaceComponent, IdeaPasswordSafePersistence, wsConfig)
                workspaces.value = wss
                wss.signInNonInteractive()
            }
            else {
                notifyDisconnected(lt)
            }
        }

        workspace.whenNotNull(lifetime) { lt, ws ->
            ws.client.connectionStatus.view(lt) { ltlt, status ->
                when (status) {
                    is ConnectionStatus.Connected -> {
                        notifyConnected(ltlt)
                    }
                }
            }
        }
    }

    suspend fun applyState(state: WorkspaceState?) {
        val lt = workspacesLifetimes.next()
        val settings = circletSettings.settings.value
        val wsConfig = ideaConfig(settings.server)
        if (state != null && settings.server.isNotBlank() && settings.enabled) {
            val wss = Workspaces(lt, this@CircletWorkspaceComponent, IdeaPasswordSafePersistence, wsConfig)
            workspaces.value = wss
            wss.signInWithWorkspaceState(state)
        }
        else {
            workspaces.value = null
        }
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
    notify(lifetime, "Not authenticated.<br> <a href=\"sign-in\">Sign in</a>", {})
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


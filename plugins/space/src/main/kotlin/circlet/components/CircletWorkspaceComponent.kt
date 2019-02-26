package circlet.components

import circlet.auth.*
import circlet.common.oauth.*
import circlet.platform.client.*
import circlet.settings.*
import circlet.utils.*
import circlet.workspaces.*
import circlet.workspaces.WorkspaceState
import com.intellij.openapi.components.*
import com.intellij.openapi.options.*
import runtime.*
import runtime.async.*
import runtime.json.*
import runtime.reactive.*

val circletWorkspace get() = application.getComponent<CircletWorkspaceComponent>()

// monitors CircletConfigurable state, creates and exposed instance of Workspace, provides various state properties and callbacks.
class CircletWorkspaceComponent : ApplicationComponent, LifetimedComponent by SimpleLifetimedComponent() {

    val workspace = mutableProperty<Workspace?>(null).apply {
        view(lifetime) { lt, ws ->
            if (ws != null) {
                ws.client.connectionStatus.view(lt) { ltlt, status ->
                    when (status) {
                        is ConnectionStatus.Connected -> {
                            notifyConnected(ltlt)
                        }
                    }
                }
            }
        }
    }

    override fun initComponent() {
        circletSettings.settings.view(lifetime) { lt, state ->
            val workspaceLifetime = lt.nested()
            launch(lt, Ui) {
                workspace.value = null
                if (state.server.isNotBlank() && state.enabled) {
                    val ps = IdeaPasswordSafePersistence
                    val wsConfig = ideaConfig(state.server)
                    val authenticator = IdeaAuthenticator(workspaceLifetime, wsConfig)
                    val wsStateText = ps.get(WorkspaceStateKey)
                    if (wsStateText != null) {
                        val wss = parseJson<WorkspaceState>(wsStateText)
                        workspace.value = wsFromState(workspaceLifetime, wss, wsConfig, authenticator, ps)
                    }
                    else {
                        notifyDisconnected(workspaceLifetime)
                    }
                }
                else {
                    notifyDisconnected(workspaceLifetime)
                }

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

}


fun ideaConfig(server: String): WorkspaceConfiguration {
    return WorkspaceConfiguration(
        server,
        IdeaOAuthConfig.clientId,
        IdeaOAuthConfig.clientSecret)
}


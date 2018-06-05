package circlet.components

import circlet.app.*
import circlet.bootstrap.*
import circlet.platform.client.*
import circlet.settings.*
import circlet.utils.*
import com.intellij.notification.*
import com.intellij.openapi.components.*
import com.intellij.openapi.options.*
import com.intellij.openapi.project.*
import com.intellij.xml.util.*
import runtime.reactive.*

class ConnectionComponent(project: Project) :
    AbstractProjectComponent(project), LifetimedComponent by SimpleLifetimedComponent() {

    val loginModel: LoginModel? get() = connection?.loginModel

    private var connection: Connection? = null

    val connected: Signal<Unit> = Signal.create()

    override fun initComponent() {
        myProject.settings.serverUrl.view(lifetime) { urlLifetime, url ->
            connection = null

            if (url.isNotBlank()) {
                connection = connections.get(url, urlLifetime).value

                loginModel!!.meUser.view(urlLifetime) { userStatusLifetime, userStatus ->
                    if (userStatus === UserStatus.NoUser) {
                        authCheckFailedNotification(userStatusLifetime)
                    }
                }

                loginModel!!.meSession.view(urlLifetime) { sessionStateLifetime, meSession ->
                    if (meSession is MeSession.ClientReady) {
                        meSession.clientSession.client.connectionStatus.view(sessionStateLifetime) { connectionStateLifetime, connectionState ->
                            when (connectionState) {
                                ConnectionStatus.CONNECTED -> notifyConnected(connectionStateLifetime)
                                ConnectionStatus.CONNECTING -> notifyReconnect(connectionStateLifetime)
                                ConnectionStatus.AUTH_FAILED -> authCheckFailedNotification(connectionStateLifetime)
                            }
                        }
                    }
                }
            }
            else {
                notifyDisconnected(urlLifetime)
            }
        }
    }

    private fun notifyReconnect(lifetime: Lifetime) {
        myProject.notify(
            lifetime,
            "Failed to establish server connection. Will keep trying to reconnect.<br> <a href=\"switch-off\">Switch off</a>",
            ::disable
        )
    }

    private fun notifyDisconnected(lifetime: Lifetime) {
        myProject.notify(
            lifetime,
            "Integration switched off.<br> <a href=\"switch-on\">Switch on</a>",
            ::enable
        )
    }

    private fun notifyConnected(lifetime: Lifetime) {
        connected.fire()

        myProject.notify(lifetime, "Signed in")
    }

    private fun authCheckFailedNotification(lifetime: Lifetime) {
        myProject.notify(
            lifetime,
            "Not authenticated.<br> <a href=\"sign-in\">Sign in</a>",
            ::authenticate
        )
    }

    private fun enable() {
        ShowSettingsUtil.getInstance().showSettingsDialog(myProject, ConnectionConfigurable::class.java)
    }

    private fun disable() {
        myProject.settings.serverUrl.value = ""
    }

    fun authenticate() {
        connection?.authenticate()
    }
}

val Project.connection: ConnectionComponent get() = getComponent()
val Project.clientOrNull: KCircletClient? get() = connection.loginModel?.clientOrNull


private fun Project.notify(lifetime: Lifetime, text: String, handler: (() -> Unit)? = null) {
    Notification(
        "Circlet",
        "Circlet",
        XmlStringUtil.wrapInHtml(text),
        NotificationType.INFORMATION,
        handler?.let { NotificationListener { _, _ -> it() } }
    ).notify(lifetime, this)
}

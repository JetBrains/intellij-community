package circlet.components

import circlet.app.*
import circlet.bootstrap.*
import circlet.client.api.*
import circlet.platform.client.*
import circlet.settings.*
import circlet.utils.*
import com.intellij.notification.*
import com.intellij.notification.Notification
import com.intellij.openapi.components.*
import com.intellij.openapi.options.*
import com.intellij.openapi.project.*
import com.intellij.xml.util.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import runtime.reactive.*
import runtime.utils.*
import java.awt.*
import java.net.*
import java.util.concurrent.*

class ConnectionComponent(project: Project) :
    AbstractProjectComponent(project), LifetimedComponent by SimpleLifetimedComponent() {

    var loginModel: LoginModel? = null
        private set

    val connected: Signal<Unit> = Signal.create()

    override fun initComponent() {
        myProject.settings.serverUrl.view(lifetime) { urlLifetime, url ->
            loginModel = null

            if (url.isNotBlank()) {
                loginModel = connections.get(url, urlLifetime).value

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

    private val seq = SequentialLifetimes(lifetime)

    fun authenticate() {
        loginModel?.let { model ->
            val lt = seq.next()
            val port = selectFreePort(10000)
            val server = embeddedServer(Jetty, port, "localhost") {
                routing {
                    get("auth") {
                        val token = call.parameters[TOKEN_PARAMETER]!!

                        model.signIn(token, "")

                        call.respondText(
                            "Authorization successful! Now you can close this page and return to the IDE.",
                            ContentType.Text.Html
                        )

                        lt.terminate()
                    }
                }
            }.start(wait = false)

            lt.add {
                server.stop(100, 5000, TimeUnit.MILLISECONDS)
            }

            Desktop.getDesktop().browse(URI(
                Navigator.login("http://localhost:$port/auth").absoluteHref(model.server)
            ))
        }
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

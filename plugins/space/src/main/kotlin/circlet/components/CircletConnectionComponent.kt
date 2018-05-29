package circlet.components

import circlet.app.*
import circlet.bootstrap.*
import circlet.client.api.*
import circlet.platform.client.*
import circlet.settings.*
import circlet.utils.*
import com.intellij.openapi.components.*
import com.intellij.openapi.options.*
import com.intellij.openapi.project.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import runtime.reactive.*
import java.awt.*
import java.net.*
import java.util.concurrent.*

class CircletConnectionComponent(project: Project) :
    AbstractProjectComponent(project), Lifetimed by LifetimedOnDisposable(project) {

    var loginModel: LoginModel? = null
        private set

    val connected: Signal<Unit> = Signal.create()

    override fun initComponent() {
        myProject.settings.serverUrl.view(lifetime) { urlLifetime, url ->
            loginModel = null

            if (url.isNotEmpty()) {
                loginModel = LoginModel(
                    persistence = IdeaPersistence.substorage("$url-"),
                    server = url,
                    appLifetime = urlLifetime,
                    notificationKind = NotificationKind.Ide
                )

                loginModel!!.meUser.view(urlLifetime) { userStatusLifetime, userStatus ->
                    if (userStatus === UserStatus.NoUser) {
                        authCheckFailedNotification(userStatusLifetime)
                    }
                }

                loginModel!!.meSession.view(urlLifetime) { sessionStateLifetime, meSession ->
                    if (meSession is MeSession.ClientReady) {
                        meSession.clientSession.client.connectionStatus.view(sessionStateLifetime) {
                            connectionStateLifetime, connectionState ->
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
        ShowSettingsUtil.getInstance().showSettingsDialog(myProject, CircletConnectionConfigurable::class.java)
    }

    private fun disable() {
        myProject.settings.serverUrl.value = ""
    }

    private val seq = SequentialLifetimes(lifetime)

    fun authenticate() {
        loginModel?.let { model ->
            val lt = seq.next()
            val server = embeddedServer(Jetty, 8080) {
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
                Navigator.login("http://localhost:8080/auth").absoluteHref(model.server)
            ))
        }
    }
}

val Project.connection: CircletConnectionComponent get() = getComponent()

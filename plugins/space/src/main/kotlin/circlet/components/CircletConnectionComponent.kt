package circlet.components

import circlet.client.*
import circlet.client.api.*
import circlet.settings.*
import circlet.utils.*
import com.intellij.concurrency.*
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
import kotlinx.coroutines.experimental.*
import runtime.reactive.*
import java.awt.*
import java.net.*
import java.util.concurrent.*

class CircletConnectionComponent(private val project: Project) :
    AbstractProjectComponent(project),
    ILifetimedComponent by LifetimedComponent(project) {

    var loginModel: LoginModel? = null
        private set

    init {
        project.settings.serverUrl.view(componentLifetime) { urlLifetime, url ->
            loginModel?.stop()
            loginModel = null

            if (url.isNotEmpty()) {
                loginModel = LoginModel(
                    IdeaPersistence.substorage("$url-"), url,
                    EmptyLoggedStateWatcher, { authCheckFailedNotification(urlLifetime) }, NotificationKind.Ide
                )

                loginModel!!.client.connectionStatus.view(urlLifetime) { stateLifetime, state ->
                    when (state) {
                        ConnectionStatus.CONNECTED -> notifyConnected()
                        ConnectionStatus.CONNECTING -> notifyReconnect(stateLifetime)
                        ConnectionStatus.AUTH_FAILED -> authCheckFailedNotification(stateLifetime)
                    }
                }

                JobScheduler.getScheduler().schedule(
                    {
                        async {
                            if (!urlLifetime.isTerminated) {
                                if (loginModel?.token() == null) {
                                    authCheckFailedNotification(urlLifetime)
                                }
                                else {
                                    loginModel?.restart()
                                }
                            }
                        }
                    },
                    100, TimeUnit.MILLISECONDS
                )
            }
            else {
                notifyDisconnected(urlLifetime)
            }
        }
    }

    private fun notifyReconnect(lifetime: Lifetime) {
        Notification(
            "Circlet",
            "Circlet",
            XmlStringUtil.wrapInHtml("Failed to establish server connection. Will keep trying to reconnect.<br> <a href=\"switch-off\">Switch off</a>"),
            NotificationType.INFORMATION,
            { _, _ -> disable() }
        ).notify(lifetime, project)
    }

    private fun notifyDisconnected(lifetime: Lifetime) {
        Notification(
            "Circlet",
            "Circlet",
            XmlStringUtil.wrapInHtml("Integration switched off.<br> <a href=\"switch-on\">Switch on</a>"),
            NotificationType.INFORMATION,
            { _, _ -> enable() }
        ).notify(lifetime, project)
    }

    private fun notifyConnected() {
        Notification(
            "Circlet",
            "Circlet",
            XmlStringUtil.wrapInHtml("Signed in"),
            NotificationType.INFORMATION,
            null
        ).notify(project)
    }

    private fun enable() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, CircletConnectionConfigurable::class.java)
    }

    private fun disable() {
        project.settings.serverUrl.value = ""
    }

    private fun authCheckFailedNotification(lifetime: Lifetime) {
        Notification(
            "Circlet",
            "Circlet",
            XmlStringUtil.wrapInHtml("Not authenticated.<br> <a href=\"sign-in\">Sign in</a>"),
            NotificationType.INFORMATION,
            { _, _ -> authenticate() }
        ).notify(lifetime, project)
    }

    private val seq = SequentialLifetimes(componentLifetime)

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

            // TODO:
        }
    }
}

val Project.connection: CircletConnectionComponent get() = component()

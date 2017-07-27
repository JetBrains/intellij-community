package circlet.components

import circlet.client.*
import runtime.async.*
import circlet.utils.*
import com.intellij.concurrency.*
import com.intellij.notification.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.xml.util.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import runtime.net.*
import runtime.reactive.*
import java.awt.*
import java.net.*
import java.util.concurrent.*

class CircletConnectionComponent(val project: Project) :
    AbstractProjectComponent(project),
    ILifetimedComponent by LifetimedComponent(project) {

    // val endpoint = "http://latest.n.circlet.labs.intellij.net"

    val endpoint = "http://localhost:8000"

    val loginDataComponent = component<CircletLoginComponent>()

    val loginModel = LoginModel(IdeaPersistence, endpoint, { authCheckFailedNotification() }) {
        // authCheckFailedNotification()
        // TODO: NOTIFY
    }

    init {
        loginDataComponent.enabled.whenTrue(componentLifetime) { enabledLt ->
            JobScheduler.getScheduler().schedule(
                {
                    async {
                        if (IdeaPersistence.get("token") ?: "" == "") {
                            authCheckFailedNotification()
                        }
                        else {
                            try {
                                KCircletClient.start(loginModel, endpoint, true)
                                KCircletClient.connection.status.view(enabledLt) { stateLt, state ->
                                    when (state) {
                                        ConnectionStatus.CONNECTED -> {
                                            notifyConnected()
                                        }
                                        ConnectionStatus.CONNECTING -> {
                                            notifyReconnect(stateLt)
                                        }
                                    }
                                }
                            }
                            catch (th: Throwable) {
                                authCheckFailedNotification()
                            }
                        }
                    }
                }, 100, TimeUnit.MILLISECONDS)
        }


        loginDataComponent.enabled.whenFalse(componentLifetime) {
            notifyDisconnected(it)
        }

    }

    fun enable() {
        loginDataComponent.enabled.value = true
    }

    fun disable() {
        loginDataComponent.enabled.value = false
    }

    private fun notifyReconnect(lt: Lifetime) {
        val notification = Notification(
            "IdePLuginClient.notifyReconnect",
            "Circlet",
            XmlStringUtil.wrapInHtml("Failed to establish server connection. Will keep trying to reconnect.<br> <a href=\"update\">Switch off</a>"),
            NotificationType.INFORMATION,
            { a, b -> disable() })
        notification.notify(lt, project)
    }

    fun notifyDisconnected(lt: Lifetime) {
        val notification = Notification(
            "IdePLuginClient.notifyDisconnected",
            "Circlet",
            XmlStringUtil.wrapInHtml("Integration switched off.<br> <a href=\"update\">Switch on</a>"),
            NotificationType.INFORMATION,
            { a, b -> enable() })
        notification.notify(lt, project)
    }

    fun notifyConnected() {
        val notification = Notification(
            "IdePLuginClient.notifyDisconnected",
            "Circlet",
            XmlStringUtil.wrapInHtml("Signed in"),
            NotificationType.INFORMATION,
            { a, b -> enable() })
        notification.notify(project)
    }

    private fun authCheckFailedNotification() {
        Notification(
            "IdePLuginClient.authCheckFailedNotification",
            "Circlet",
            XmlStringUtil.wrapInHtml("Not authenticated.<br> <a href=\"update\">Sign in with JBA</a>"),
            NotificationType.INFORMATION,
            { a, b -> authenticate() })
            .notify(project)
    }

    val seq = SequentialLifetimes(componentLifetime)

    fun authenticate() {
        val lt = seq.next()
        val ser = embeddedServer(Jetty, 8080) {
            routing {
                get("auth") {
                    val jwt = call.parameters["jwt"]!!
                    loginModel.signIn(jwt)
                    call.respondText("Hello, world!", ContentType.Text.Html)
                    lt.terminate()
                }
            }
        }.start(wait = false)

        lt.add {
            ser.stop(100, 5000, TimeUnit.MILLISECONDS)
        }
        Desktop.getDesktop().browse(URI("http://intdevsrv.labs.intellij.net:8081/profile/jwt-auth/circlet?auth_url=${urlEncode("http://localhost:8080/auth")}"))

        // TODO:
    }
}


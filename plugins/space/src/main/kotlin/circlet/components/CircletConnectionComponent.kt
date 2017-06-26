package circlet.components

import circlet.client.*
import runtime.async.*
import circlet.utils.*
import com.intellij.concurrency.*
import com.intellij.notification.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.xml.util.*
import klogging.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import runtime.reactive.*
import java.util.concurrent.*

private val log = KLoggers.logger("plugin/IdePluginClient.kt")

class CircletConnectionComponent(val project: Project) :
    AbstractProjectComponent(project),
    ILifetimedComponent by LifetimedComponent(project) {

    val loginDataComponent = component<CircletLoginComponent>()
    val refreshLifetimes = SequentialLifetimes(componentLifetime)

    init {
        loginDataComponent.enabled.whenTrue(componentLifetime) { enabledLt ->
            loginDataComponent.token.view(enabledLt) { tokenLt, tk ->
                val refreshLt = refreshLifetimes.next()
                tokenLt.add { refreshLt.terminate() }

                JobScheduler.getScheduler().schedule(
                    {
                        if (!refreshLt.isTerminated) {
                            async {
                                if (IdeaPersistence.get("token") ?: "" == "") {
                                    authCheckFailedNotification()
                                }
                                else {
                                    try {
                                        KCircletClient.start(loginDataComponent.loginModel, loginDataComponent.endpoint, false)
                                        KCircletClient.connection.status.forEach(refreshLt) { status ->
                                            when (status) {
                                                ConnectionStatus.CONNECTED -> notifyConnected()
                                                ConnectionStatus.AUTH_FAILED -> authCheckFailedNotification()
                                            }
                                        }
                                    }
                                    catch (th: Throwable) {
                                        refreshLt.terminate()
                                        authCheckFailedNotification()
                                    }
                                }
                            }
                        }
                    }, 100, TimeUnit.MILLISECONDS)
            }
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
            { a, b ->
                authenticate()
            })
            .notify(project)
    }

    fun authenticate() {
        val lt = Lifetime()
        val ser = embeddedServer(Jetty, 8080) {
            routing {
                get("") {
                    val token = call.parameters["token"]!!
                    loginDataComponent.setToken(token)
                    lt.terminate()
                    call.respondText("Hello, world!", ContentType.Text.Html)
                }
            }
        }.start(wait = true)
        lt.add {
            ser.stop(100, 5000, TimeUnit.MILLISECONDS)
        }

    }
}


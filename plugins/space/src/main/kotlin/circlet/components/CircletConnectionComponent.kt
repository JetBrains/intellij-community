package circlet.components

import circlet.*
import runtime.async.*
import circlet.login.*
import circlet.utils.*
import com.intellij.concurrency.*
import com.intellij.notification.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.xml.util.*
import klogging.*
import runtime.reactive.*
import java.util.concurrent.*

private val log = KLoggers.logger("plugin/IdePluginClient.kt")

class CircletConnectionComponent(val project: Project) :
    AbstractProjectComponent(project),
    ILifetimedComponent by LifetimedComponent(project) {

    val loginDataComponent = component<CircletLoginComponent>()
    val refreshLifetimes = SequentialLifetimes(componentLifetime)
    val client = mutableProperty<CircletClient?>(null)

    init {
        loginDataComponent.token.view(componentLifetime) { tklt, tk ->
            loginDataComponent.enabled.whenTrue(tklt) { enabledLt ->
                loginDataComponent.url.view(enabledLt) { urllt, url ->
                    loginDataComponent.orgName.view(urllt) { orgLt, orgName ->
                        val refreshLt = refreshLifetimes.next()
                        JobScheduler.getScheduler().schedule({
                            if (!refreshLt.isTerminated) {
                                async {
                                    try {
                                        val client = CircletClient(refreshLt)
                                        client.connected.whenTrue(refreshLt) { ntlt ->
                                            notifyConnected()
                                            this@CircletConnectionComponent.client.value = client
                                        }
                                        client.failed.whenTrue(refreshLt) { ntlt ->
                                            notifyReconnect(ntlt)
                                        }
                                        client.start(IdeaPersistence, url, orgName)
                                    } catch (th: Throwable) {
                                        refreshLt.terminate()
                                        authCheckFailedNotification()
                                    }
                                }
                            }
                        }, 100, TimeUnit.MILLISECONDS)
                    }
                }
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

    fun askPassword() {
        LoginDialog(LoginDialogViewModel(component<CircletLoginComponent>())).show()
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
            XmlStringUtil.wrapInHtml("Logged in to ${loginDataComponent.orgName.value}"),
            NotificationType.INFORMATION,
            { a, b -> enable() })
        notification.notify(project)
    }

    private fun authCheckFailedNotification() {
        Notification(
            "IdePLuginClient.authCheckFailedNotification",
            "Circlet",
            XmlStringUtil.wrapInHtml("Authorization failed.<br> <a href=\"update\">Re-enter credentials</a>"),
            NotificationType.INFORMATION,
            { a, b -> project.component<CircletConnectionComponent>().askPassword() })
            .notify(project)
    }
}


package circlet.components

import circlet.*
import circlet.login.*
import circlet.utils.*
import com.intellij.concurrency.*
import com.intellij.notification.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.xml.util.*
import klogging.*
import nl.komponents.kovenant.*
import runtime.*
import runtime.kdata.*
import runtime.klogger.*
import runtime.lifetimes.*
import runtime.reactive.*
import runtime.random.*
import java.util.concurrent.*

private val log = KLoggers.logger("plugin/IdePluginClient.kt")

enum class ConnectingState {
    Disconnected,
    Connected,
    AuthFailed,
    TryConnect
}

class IdePluginClient(val project: Project) :
    AbstractProjectComponent(project),
    ILifetimedComponent by LifetimedComponent(project) {

    val loginDataComponent = component<CircletLoginComponent>()

    var attemptToConnectNotificationShown = false

    val state = Property.createMutable(ConnectingState.Disconnected)
    val askPasswordExplicitlyAllowed = Property.createMutable(false)
    val client = Property.createMutable<CircletClient?>(null)

    init {

//        loginDataComponent.enabled.filter { it }.forEach(componentLifetime) { enabledLt ->
//            state.value = ConnectingState.TryConnect
//
//            enabledLt.add {
//                disconnect()
//            }
//        }

//        state.view(componentLifetime) { lt, state ->
//            when (state) {
//                ConnectingState.TryConnect -> tryReconnect(lt)
//                ConnectingState.AuthFailed -> authCheckFailedNotification()
//                ConnectingState.Connected -> notifyConnected()
//                ConnectingState.Disconnected -> notifyDisconnected(lt)
//            }
//        }
//
//        loginDataComponent.credentialsUpdated.advise(componentLifetime, {
//            state.value = ConnectingState.TryConnect
//        })

/*
        ProgressManager.getInstance().run(externalTask(myProject, true, object: IExternalTask {
            override val lifetime: Lifetime get() = connectingProgressLifetimeDef.lifetime
            override val cancel: () -> Unit get() = { disconnect() }
            override val title: String get() = connectionState?.message ?: "Connecting"
            override val header: String get() = connectionState?.message ?: "Connecting"
            override val description: String get() = connectionState?.message ?: "Connecting"
            override val isIndeterminate: Boolean get() = true
            override val progress: Double get() = 0.5
        }))
*/
    }

    fun enable() {
        askPasswordExplicitlyAllowed.value = true
        loginDataComponent.enabled.value = true
    }

    fun disable() {
        loginDataComponent.enabled.value = false
    }

    private fun tryReconnect(lifetime: Lifetime) {

        val ask = askPasswordExplicitlyAllowed.value
        askPasswordExplicitlyAllowed.value = false

        val loginComponent = component<CircletLoginComponent>()
        val modality = application.currentModalityState

        task {
            log.catch {
                loginComponent.
                    getAccessToken(loginComponent.login, loginComponent.pass).
                    thenLater(lifetime, modality) {
                        val errorMessage = it.errorMessage
                        if (errorMessage == null || errorMessage.isEmpty()) {
                            state.value = ConnectingState.Connected
                            val token = it.token!!
                            connectWithToken(lifetime, token)
                        } else {
                            state.value = ConnectingState.AuthFailed
                            if (ask)
                                askPassword()
                        }
                    }.failureLater(lifetime, modality) {
                    notifyReconnect(lifetime)
                    JobScheduler.getScheduler().schedule({
                        if (!lifetime.isTerminated)
                            tryReconnect(lifetime)
                    }, 5000, TimeUnit.MILLISECONDS)
                    state.value = ConnectingState.TryConnect
                }
            }
        }
    }

    fun askPassword() {
        LoginDialog(LoginDialogViewModel(component<CircletLoginComponent>())).show()
    }

    private fun connectWithToken(lifetime: Lifetime, token: String) {
        val clientLocal = CircletClient("ws://localhost:8084/api/v1/connect", token)
        client.value = clientLocal
        clientLocal.services.user.isMyProfileReady()
            .flatMap {
                if (!it) {
                    clientLocal.services.user.createProfile("your", "name", null)
                } else {
                    clientLocal.services.user.getMyUid().map {
                        log.debug { "My Profile: $it" }
                    }
                    clientLocal.services.user.editUsername("heytwo")
                }
            }
            .then {
                log.debug { "Result: $it" }
            }
            .failure {
                log.debug { "Error: $it" }
            }
    }

    fun disconnect() {
        state.value = ConnectingState.Disconnected
    }

    private fun notifyReconnect(lt: Lifetime) {
        if (attemptToConnectNotificationShown)
            return
        val notification = Notification(
            "IdePLuginClient.notifyReconnect",
            "Circlet",
            XmlStringUtil.wrapInHtml("Failed to establish server connection. Will keep trying to reconnect.<br> <a href=\"update\">Switch off</a>"),
            NotificationType.INFORMATION,
            { a, b -> disable() })
        notification.notify(lt, project)
        attemptToConnectNotificationShown = true
        lt.inContext { afterTermination { attemptToConnectNotificationShown = false } }
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
            XmlStringUtil.wrapInHtml("Logged in as ${loginDataComponent.login}"),
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
            { a, b -> project.component<IdePluginClient>().askPassword() })
            .notify(project)
    }
}



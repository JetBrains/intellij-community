package circlet.components

import runtime.async.*
import circlet.login.*
import circlet.reactive.*
import circlet.utils.*
import com.intellij.notification.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.xml.util.*
import klogging.*
import runtime.reactive.*

private val log = KLoggers.logger("plugin/IdePluginClient.kt")

class IdePluginClient(val project: Project) :
    AbstractProjectComponent(project),
    ILifetimedComponent by LifetimedComponent(project) {

    val loginDataComponent = component<CircletLoginComponent>()

    val app = Property.createMutable<IdeaCircletApp?>(null)

    init {

        loginDataComponent.enabled.whenTrue(componentLifetime) { enabledLt ->
            async {
                val ideaApp = IdeaCircletApp(enabledLt)
                ideaApp.start(IdeaPersistence, "http://localhost")
                app.value = ideaApp
            }
            enabledLt.add { app.value = null }
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
            XmlStringUtil.wrapInHtml("Logged in"),
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


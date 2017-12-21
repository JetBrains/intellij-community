package circlet.actions

import circlet.client.*
import circlet.client.api.*
import circlet.components.*
import circlet.utils.*
import com.intellij.notification.*
import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.*
import klogging.*
import runtime.async.*

@Suppress("unused")
private val log = KLoggers.logger("plugin/TestCircletAction.kt")

class TestCircletAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null && component<CircletLoginComponent>().enabled.value
                && KCircletClient.connectionStatus.value == ConnectionStatus.CONNECTED
    }

    override fun actionPerformed(e: AnActionEvent) {
        async {
            val res = service<Me>().info()

            application.invokeLater {
                Notification(
                    "IdeaPluginClient",
                    "Circlet check",
                    "Me = $res",
                    NotificationType.INFORMATION
                ).notify(e.project)
            }
        }
    }
}

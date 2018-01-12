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
        val project = e.project

        e.presentation.isEnabledAndVisible =
            project != null && component<CircletLoginComponent>().enabled.value
            && project.component<CircletConnectionComponent>().loginModel.client.connectionStatus.value == ConnectionStatus.CONNECTED
    }

    override fun actionPerformed(e: AnActionEvent) {
        async {
            val project = e.project!!
            val res = project.component<CircletConnectionComponent>().loginModel.client.service<Me>().info()

            application.invokeLater {
                Notification(
                    "IdeaPluginClient",
                    "Circlet check",
                    "Me = $res",
                    NotificationType.INFORMATION
                ).notify(project)
            }
        }
    }
}

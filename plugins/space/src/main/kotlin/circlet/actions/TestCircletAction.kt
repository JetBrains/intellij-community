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

private val log = KLoggers.logger("plugin/TestCircletAction.kt")

class TestCircletAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.project ?: return

        val clc = component<CircletLoginComponent>()
        val enabled = clc.enabled.value
        val connected = KCircletClient.connection.status.value != ConnectionStatus.AUTH_FAILED

        e.presentation.isEnabled = enabled && connected
        e.presentation.isVisible = enabled && connected
    }

    override fun actionPerformed(e: AnActionEvent) {
        async {
            val res = service<HealthCheck>().check()
            application.invokeLater {
                Notification(
                    "IdePLuginClient",
                    "Circlet check",
                    "res = $res",
                    NotificationType.INFORMATION)
                    .notify(e.project)
            }
        }
    }
}


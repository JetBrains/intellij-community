package circlet.actions

import circlet.client.*
import circlet.components.*
import circlet.platform.client.*
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.*
import klogging.*
import kotlinx.coroutines.experimental.*
import runtime.*

@Suppress("unused")
private val log = KLoggers.logger("plugin/TestCircletAction.kt")

class TestCircletAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project?.connection?.loginModel?.client?.connectionStatus?.value == ConnectionStatus.CONNECTED
    }

    override fun actionPerformed(e: AnActionEvent) {
        async(UiDispatch.coroutineContext) {
            val project = e.project!!
            val result = project.connection.loginModel!!.client.me.info()
            Notification(
                "Circlet",
                "Circlet check",
                "Me = $result",
                NotificationType.INFORMATION
            ).notify(project)
        }
    }
}

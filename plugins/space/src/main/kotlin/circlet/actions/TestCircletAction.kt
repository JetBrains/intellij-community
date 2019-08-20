package circlet.actions

import circlet.components.*
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.*
import kotlinx.coroutines.*
import platform.common.*
import runtime.*

class TestCircletAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = circletWorkspace.workspace.value != null
    }

    override fun actionPerformed(e: AnActionEvent) {

        GlobalScope.launch(Ui, CoroutineStart.DEFAULT) {
            val project = e.project!!

            Notification(
                ProductName,
                "$ProductName check",
                "Hello, this is a fake check",
                NotificationType.INFORMATION
            ).notify(project)

        }
    }
}

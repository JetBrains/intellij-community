package circlet.actions

import circlet.components.space
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import platform.common.ProductName
import runtime.Ui

class TestCircletAction : AnAction() {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = space.workspace.value != null
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

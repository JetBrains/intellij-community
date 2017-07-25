package training.editor.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Created by karashevich on 16/09/15.
 */
class HideProjectTreeAction : DumbAwareAction(), LearnActions {

  override val actionId: String
    get() = myActionId

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val windowManager = ToolWindowManager.getInstance(project)
    if (!ApplicationManager.getApplication().isUnitTestMode) windowManager.getToolWindow(PROJECT_ID).hide(null)
  }

  override fun unregisterAction() {}

  companion object {
    private val PROJECT_ID = "Project"
    val myActionId = "LearnHideProjectTreeAction"
  }
}

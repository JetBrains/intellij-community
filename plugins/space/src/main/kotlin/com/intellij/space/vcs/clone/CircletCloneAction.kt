package circlet.vcs.clone

import circlet.actions.CircletActionUtils
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.util.ui.cloneDialog.VcsCloneDialog

class CircletCloneAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val projectExists = e.project != null
    e.presentation.isEnabledAndVisible = projectExists
    CircletActionUtils.showIconInActionSearch(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    runClone(project)
  }

  companion object {
    fun runClone(project: Project) {
      val checkoutListener = ProjectLevelVcsManager.getInstance(project).compositeCheckoutListener
      val dialog = VcsCloneDialog.Builder(project).forExtension(CircletCloneExtension::class.java)
      if (dialog.showAndGet()) {
        dialog.doClone(checkoutListener)
      }
    }
  }
}

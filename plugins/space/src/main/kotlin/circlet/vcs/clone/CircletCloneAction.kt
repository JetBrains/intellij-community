package circlet.vcs.clone

import circlet.actions.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.*
import com.intellij.openapi.vcs.*
import com.intellij.util.ui.cloneDialog.*

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

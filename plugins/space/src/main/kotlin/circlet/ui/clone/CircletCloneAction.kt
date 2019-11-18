package circlet.ui.clone

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.*
import com.intellij.openapi.vcs.*
import com.intellij.util.ui.cloneDialog.*
import icons.*

class CircletCloneAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
        val projectExists = e.project != null
        e.presentation.isEnabledAndVisible = projectExists
        e.presentation.icon = if (e.place == ActionPlaces.ACTION_SEARCH) CircletIcons.mainIcon else null
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

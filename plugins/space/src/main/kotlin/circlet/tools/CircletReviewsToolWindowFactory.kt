package circlet.tools

import circlet.messages.*
import circlet.utils.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.*
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.*
import com.intellij.ui.content.*

class CircletReviewsToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CircletReviewsToolWindowPanel(project)
        val content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false)

        Disposer.register(content, panel)

        content.isCloseable = false

        val contentManager = toolWindow.contentManager

        contentManager.addContent(content)

        contentManager.addContentManagerListener(object : ContentManagerAdapter() {
            override fun contentAdded(event: ContentManagerEvent) {
                content.updateDisplayName()
            }

            override fun contentRemoved(event: ContentManagerEvent) {
                content.updateDisplayName()
            }
        })

        project.toolWindowManagerEx.addToolWindowManagerListener(object : ToolWindowManagerAdapter() {
            private var previouslyVisible = toolWindow.isVisible

            override fun stateChanged() {
                val visible = project.reviewsToolWindow?.isVisible ?: return

                if (visible && !previouslyVisible) {
                    panel.reload()
                }

                previouslyVisible = visible
            }
        })
    }

    companion object {
        const val TOOL_WINDOW_ID = "Code reviews"
    }
}

val Project.reviewsToolWindow: ToolWindow?
    get() = computeSafe {
        toolWindowManager.getToolWindow(CircletReviewsToolWindowFactory.TOOL_WINDOW_ID)
    }

private fun Content.updateDisplayName() {
    displayName = if (manager.contentCount > 1) {
        CircletBundle.message("review-list-content.display-name")
    }
    else {
        ""
    }
}

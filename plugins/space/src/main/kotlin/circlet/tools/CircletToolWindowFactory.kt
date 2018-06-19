package circlet.tools

import circlet.utils.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.*
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.*
import com.intellij.ui.content.*

class CircletToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ReviewsToolWindowPanel(project)
        val content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false)

        Disposer.register(content, panel)

        content.isCloseable = false

        val toolWindowVisible = toolWindow.isVisible

        project.toolWindowManagerEx.addToolWindowManagerListener(object : ToolWindowManagerAdapter() {
            private var previouslyVisible = toolWindowVisible

            override fun stateChanged() {
                val visible = project.toolWindow?.isVisible ?: return

                if (visible && !previouslyVisible) {
                    panel.reload()
                }

                previouslyVisible = visible
            }
        })

        toolWindow.contentManager.addContent(content)

        if (toolWindowVisible) {
            panel.reload()
        }
    }

    companion object {
        const val TOOL_WINDOW_ID = "Circlet"
    }
}

val Project.toolWindow: ToolWindow?
    get() = computeSafe {
        toolWindowManager.getToolWindow(CircletToolWindowFactory.TOOL_WINDOW_ID)
    }

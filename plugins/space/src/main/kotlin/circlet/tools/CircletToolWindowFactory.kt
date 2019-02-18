package circlet.tools

import circlet.utils.*
import com.intellij.openapi.project.*
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.*
import com.intellij.ui.components.*
import com.intellij.ui.content.*

class CircletToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = Panel(title = null)

        val content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false)

        content.isCloseable = false

        val toolWindowVisible = toolWindow.isVisible

        project.toolWindowManagerEx.addToolWindowManagerListener(object : ToolWindowManagerAdapter() {
            override fun stateChanged() {

            }
        })

        toolWindow.contentManager.addContent(content)

        if (toolWindowVisible) {
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

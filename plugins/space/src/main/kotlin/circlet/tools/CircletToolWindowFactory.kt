package circlet.tools

import circlet.plugins.pipelines.ui.*
import circlet.utils.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.wm.*
import platform.common.*

class CircletToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun shouldBeAvailable(project: Project): Boolean {
        return false
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.service<CircletToolWindowService>()
        service.createToolWindowContent(toolWindow)
    }

    override fun isDoNotActivateOnStart(): Boolean {
        return true
    }

    companion object {
        const val TOOL_WINDOW_ID = "Space Automation"
    }

}

val Project.spaceKtsToolwindow: ToolWindow?
    get() = computeSafe {
        toolWindowManager.getToolWindow(CircletToolWindowFactory.TOOL_WINDOW_ID)
    }

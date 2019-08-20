package circlet.tools

import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.ui.*
import circlet.utils.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.*
import com.intellij.ui.components.*
import com.intellij.ui.content.*
import platform.common.*

class CircletToolWindowFactory : ToolWindowFactory, DumbAware, LifetimedComponent by SimpleLifetimedComponent() {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = Panel(title = null)

        val circletModelStore = ServiceManager.getService(project, CircletModelStore::class.java)
        panel.add(CircletScriptsViewFactory().createView(lifetime, project, circletModelStore.viewModel))

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
        const val TOOL_WINDOW_ID = ProductName
    }
}

val Project.toolWindow: ToolWindow?
    get() = computeSafe {
        toolWindowManager.getToolWindow(CircletToolWindowFactory.TOOL_WINDOW_ID)
    }

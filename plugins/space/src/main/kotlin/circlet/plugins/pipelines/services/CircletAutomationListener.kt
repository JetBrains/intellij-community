package circlet.plugins.pipelines.services

import circlet.plugins.pipelines.ui.*
import circlet.plugins.pipelines.viewmodel.*
import circlet.utils.*
import com.intellij.execution.ui.*
import com.intellij.icons.*
import com.intellij.ide.impl.*
import com.intellij.openapi.project.*
import com.intellij.openapi.wm.*
import com.intellij.ui.content.*

class CircletAutomationListener(val project: Project, val toolWindowManager: ToolWindowManager): LifetimedComponent by SimpleLifetimedComponent() {

    companion object {
        const val TOOL_WINDOW_ID = "Circlet Log"
    }

    private val viewContext: CircletAutomationOutputViewContext by lazy {
        createViewContext()
    }

    fun listen(viewModel: ScriptWindowViewModel) {
        viewModel.logRunData.forEach(lifetime) {
            val data = it
            val view = viewContext.view.runLogView
            view.clear()
            view.print(data?.dummy ?: "empty", ConsoleViewContentType.NORMAL_OUTPUT)
        }

        viewModel.logBuildData.forEach(lifetime) {
            val data = it
            val view = viewContext.view.buildLogView
            view.clear()
            view.print(data?.dummy ?: "empty", ConsoleViewContentType.NORMAL_OUTPUT)
        }

    }

    private fun createViewContext(): CircletAutomationOutputViewContext {
        val toolWindow = getOrRegisterToolWindow()
        val contentManager = toolWindow.contentManager
        val view = CircletAutomationOutputViewFactory().create(project)
        toolWindow.contentManager.addContent(contentManager.factory.createContent(view.buildLogView, "Build", false))
        toolWindow.contentManager.addContent(contentManager.factory.createContent(view.runLogView, "Run", false))
        return CircletAutomationOutputViewContext(view)
    }

    private fun getOrRegisterToolWindow() : ToolWindow {
        var toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID)
        if (toolWindow != null)
            return toolWindow
        toolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_ID, true, ToolWindowAnchor.BOTTOM, project, true, false)

        val contentManager = toolWindow.contentManager
        contentManager.addContentManagerListener(object : ContentManagerAdapter() {
            override fun contentRemoved(event: ContentManagerEvent) {
                toolWindowManager.unregisterToolWindow(TOOL_WINDOW_ID)
            }
        })
        toolWindow.title = ""
        toolWindow.icon = AllIcons.Modules.Output
        // Required for hiding window without content
        ContentManagerWatcher(toolWindow, contentManager)
        return toolWindow
    }
}

class CircletAutomationOutputViewContext(
    val view: CircletAutomationOutputView)

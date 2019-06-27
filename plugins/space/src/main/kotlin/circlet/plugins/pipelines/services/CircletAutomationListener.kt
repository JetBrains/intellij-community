package circlet.plugins.pipelines.services

import circlet.plugins.pipelines.ui.*
import circlet.plugins.pipelines.viewmodel.*
import circlet.utils.*
import com.intellij.build.*
import com.intellij.build.events.*
import com.intellij.build.events.impl.*
import com.intellij.execution.ui.*
import com.intellij.icons.*
import com.intellij.ide.impl.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.model.*
import com.intellij.openapi.externalSystem.model.task.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.*
import com.intellij.openapi.wm.*
import com.intellij.ui.content.*
import runtime.reactive.*

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

        val logBuildLifetimes = SequentialLifetimes(lifetime)
        viewModel.logBuildData.forEach(lifetime) {
            val data = it
            val lt = logBuildLifetimes.next()

            if (data != null) {

                val projectSystemId = ProjectSystemId("CircletAutomation")
                val taskId = ExternalSystemTaskId.create(projectSystemId, ExternalSystemTaskType.RESOLVE_PROJECT, project)
                val descriptor = DefaultBuildDescriptor(taskId, "Sync DSL", project.basePath!!, System.currentTimeMillis())
                val result = Ref<BuildProgressListener>()
                ApplicationManager.getApplication().invokeAndWait { result.set(ServiceManager.getService(project, SyncDslViewManager::class.java)) }
                val view = result.get()
                view.onEvent(StartBuildEventImpl(descriptor, "Sync DSL ${project.name}"))
                viewModel.modelBuildIsRunning.forEach(lt) {buildIsRunning ->
                    if (!buildIsRunning) {
                        view.onEvent(FinishBuildEventImpl(descriptor.id, null, System.currentTimeMillis(), "finished", SuccessResultImpl(false)))
                        //view.onEvent(FinishBuildEventImpl(descriptor.id, null, System.currentTimeMillis(), "finished", FailureResultImpl(emptyList())))
                    }
                }

                data.messages.change.forEach(lt) {
                    //todo reimplement work with getting new message
                    val message = data.messages[it.index]
                    val detailedMessage = if (message.length > 50) message else null
                    view.onEvent(MessageEventImpl(descriptor.id, MessageEvent.Kind.SIMPLE, "log", message, detailedMessage))
                }
            }
        }

    }

    private fun createViewContext(): CircletAutomationOutputViewContext {
        val toolWindow = getOrRegisterToolWindow()
        val contentManager = toolWindow.contentManager
        val view = CircletAutomationOutputViewFactory().create(project)
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

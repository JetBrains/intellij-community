package circlet.plugins.pipelines.services

import circlet.plugins.pipelines.viewmodel.*
import circlet.utils.*
import com.intellij.build.*
import com.intellij.build.events.*
import com.intellij.build.events.impl.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.model.*
import com.intellij.openapi.externalSystem.model.task.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.*
import libraries.coroutines.extra.*
import runtime.reactive.*

class CircletAutomationListener(val lifetime : Lifetime, val project: Project) {

    fun listen(viewModel: ScriptWindowViewModel) {
        val application = ApplicationManager.getApplication()
        if (application.isUnitTestMode || application.isHeadlessEnvironment) {
            return
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
                view.onEvent(this, StartBuildEventImpl(descriptor, "Sync DSL ${project.name}"))
                viewModel.modelBuildIsRunning.forEach(lt) {buildIsRunning ->
                    if (!buildIsRunning) {
                        view.onEvent(this, FinishBuildEventImpl(descriptor.id, null, System.currentTimeMillis(), "finished", SuccessResultImpl(false)))
                        //view.onEvent(FinishBuildEventImpl(descriptor.id, null, System.currentTimeMillis(), "finished", FailureResultImpl(emptyList())))
                    }
                }

                data.messages.change.forEach(lt) {
                    //todo reimplement work with getting new message
                    val message = data.messages[it.index]
                    val detailedMessage = if (message.length > 50) message else null
                    view.onEvent(this, MessageEventImpl(descriptor.id, MessageEvent.Kind.SIMPLE, "log", message, detailedMessage))
                }
            }
        }
    }
}

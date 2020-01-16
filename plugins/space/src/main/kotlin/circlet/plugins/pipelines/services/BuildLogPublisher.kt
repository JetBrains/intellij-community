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
import libraries.coroutines.extra.*
import libraries.io.random.*
import runtime.reactive.*

fun publishBuildLog(project: Project, data: LogData) {
    val buildId = Random.nextUID()

    if (!application.isUnitTestMode && !application.isHeadlessEnvironment) {
        val projectSystemId = ProjectSystemId("CircletAutomation")
        val taskId = ExternalSystemTaskId.create(projectSystemId, ExternalSystemTaskType.RESOLVE_PROJECT, project)
        val descriptor = DefaultBuildDescriptor(taskId, "Sync DSL", project.basePath!!, System.currentTimeMillis())

        val view = ServiceManager.getService(project, SyncDslViewManager::class.java)

        view.onEvent(buildId, StartBuildEventImpl(descriptor, "Sync DSL ${project.name}"))

        if (!data.lifetime.isTerminated) {
            data.messages.change.forEach(data.lifetime) { change ->
                when (change) {
                    is ObservableList.Change.Add<String> -> {
                        val message = change.newValue
                        val detailedMessage = if (message.length > 50) message else null
                        view.onEvent(buildId, MessageEventImpl(descriptor.id, MessageEvent.Kind.SIMPLE, "log", message, detailedMessage))
                    }
                    else -> {
                        //
                    }
                }

            }
        }
        data.lifetime.addOrCallImmediately {
            view.onEvent(buildId, FinishBuildEventImpl(descriptor.id, null, System.currentTimeMillis(), "finished", SuccessResultImpl(false)))
        }

    }
}

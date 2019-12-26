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
import libraries.io.random.*

fun publishBuildLog(lifetime: Lifetime, project : Project,  data: LogData) {
    val buildId = Random.nextUID()

    if (!application.isUnitTestMode && !application.isHeadlessEnvironment) {
        val projectSystemId = ProjectSystemId("CircletAutomation")
        val taskId = ExternalSystemTaskId.create(projectSystemId, ExternalSystemTaskType.RESOLVE_PROJECT, project)
        val descriptor = DefaultBuildDescriptor(taskId, "Sync DSL", project.basePath!!, System.currentTimeMillis())

        // wtf?
        val result = Ref<BuildProgressListener>()
        ApplicationManager.getApplication().invokeAndWait { result.set(ServiceManager.getService(project, SyncDslViewManager::class.java)) }
        val view = result.get()

        view.onEvent(buildId, StartBuildEventImpl(descriptor, "Sync DSL ${project.name}"))

        lifetime.add {
            view.onEvent(buildId, FinishBuildEventImpl(descriptor.id, null, System.currentTimeMillis(), "finished", SuccessResultImpl(false)))
        }

        data.messages.change.forEach(lifetime) {
            //todo: reimplement work with getting new message
            val message = data.messages[it.index]
            val detailedMessage = if (message.length > 50) message else null
            view.onEvent(buildId, MessageEventImpl(descriptor.id, MessageEvent.Kind.SIMPLE, "log", message, detailedMessage))
        }
    }
}

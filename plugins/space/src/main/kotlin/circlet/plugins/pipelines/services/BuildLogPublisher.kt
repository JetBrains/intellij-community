package circlet.plugins.pipelines.services

import circlet.plugins.pipelines.viewmodel.*
import circlet.utils.*
import com.intellij.build.*
import com.intellij.build.events.*
import com.intellij.build.events.impl.*
import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.model.*
import com.intellij.openapi.externalSystem.model.task.*
import com.intellij.openapi.project.*
import runtime.reactive.*

fun publishBuildLog(project: Project, data: LogData) {

    if (!application.isUnitTestMode && !application.isHeadlessEnvironment) {

        val descriptor = DefaultBuildDescriptor(data.buildId, "Sync DSL", project.basePath!!, System.currentTimeMillis())

        val view = ServiceManager.getService(project, SyncDslViewManager::class.java)

        view.onEvent(data.buildId, StartBuildEventImpl(descriptor, "Sync DSL ${project.name}"))

        view.onEvent(data.buildId, MessageEventImpl(data.buildId, MessageEvent.Kind.INFO, null, "Compiling script", null))

        if (!data.lifetime.isTerminated) {
            data.messages.change.forEach(data.lifetime) { change ->
                when (change) {
                    is ObservableList.Change.Add<BuildEvent> -> {
                        val message = change.newValue
                        view.onEvent(data.buildId, message)
                    }
                    else -> {
                    }
                }
            }
        }

        data.lifetime.addOrCallImmediately {
            view.onEvent(data.buildId, FinishBuildEventImpl(descriptor.id, null, System.currentTimeMillis(), "finished", SuccessResultImpl(false)))
        }

    }
}

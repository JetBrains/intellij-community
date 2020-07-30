package com.intellij.space.plugins.pipelines.services

import com.intellij.space.plugins.pipelines.viewmodel.LogData
import com.intellij.space.utils.application
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.events.impl.StartBuildEventImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import runtime.reactive.ObservableList

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
      view.onEvent(data.buildId,
                   FinishBuildEventImpl(descriptor.id, null, System.currentTimeMillis(), "finished", SuccessResultImpl(false)))
    }

  }
}

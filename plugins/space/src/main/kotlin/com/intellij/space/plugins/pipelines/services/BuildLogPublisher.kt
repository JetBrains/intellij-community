// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services

import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.events.impl.StartBuildEventImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.plugins.pipelines.viewmodel.LogData
import runtime.reactive.ObservableList

fun publishBuildLog(project: Project, data: LogData) {
  if (!ApplicationManager.getApplication().isUnitTestMode && !ApplicationManager.getApplication().isHeadlessEnvironment) {
    val descriptor = DefaultBuildDescriptor(data.buildId, SpaceBundle.message("build.log.title"), project.basePath!!,
                                            System.currentTimeMillis())

    val view = ServiceManager.getService(project, SyncDslViewManager::class.java)

    view.onEvent(data.buildId, StartBuildEventImpl(descriptor, SpaceBundle.message("build.log.message.started", project.name)))

    view.onEvent(data.buildId, MessageEventImpl(data.buildId, MessageEvent.Kind.INFO, null,
                                                SpaceBundle.message("build.log.message.process"), null))

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
                   FinishBuildEventImpl(descriptor.id, null, System.currentTimeMillis(), SpaceBundle.message("build.log.message.finished"),
                                        SuccessResultImpl(false)))
    }

  }
}

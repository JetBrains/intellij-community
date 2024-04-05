// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.cache

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HuggingFaceCacheUpdateHandler(private val project: Project) : Disposable {
  private val connection: MessageBusConnection = project.messageBus.connect(this)

  init {
    connection.subscribe(HuggingFaceCacheUpdateListener.TOPIC, object : HuggingFaceCacheUpdateListener {
      override fun cacheUpdated() = refreshReferencesInProject()
    })
  }

  private fun refreshReferencesInProject() = DaemonCodeAnalyzer.getInstance(project).restart()

  override fun dispose() {
    connection.disconnect()
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.terminal.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.sh.run.ShSelectedTerminalRunner
import com.intellij.sh.run.terminal.ShTerminalRunRequest
import com.intellij.sh.run.terminal.ShTerminalRunTopic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Receives the requests that [com.intellij.sh.run.ShRunner] sends through [ShTerminalRunTopic] and runs them in the Terminal
 * tool window of this frontend. Events arrive off the EDT on every delivery path, so the work is handed to a project scope.
 */
internal class ShTerminalRunRequestListener : ProjectRemoteTopicListener<ShTerminalRunRequest> {
  override val topic: ProjectRemoteTopic<ShTerminalRunRequest>
    get() = ShTerminalRunTopic.TOPIC

  override fun handleEvent(project: Project, event: ShTerminalRunRequest) {
    val runner = project.service<ShSelectedTerminalRunner>() as? ShFrontendTerminalRunner ?: return
    ShTerminalFrontendScope.getInstance(project).coroutineScope.launch {
      runner.run(project, event)
    }

  }
}

@Service(Service.Level.PROJECT)
internal class ShTerminalFrontendScope(val coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): ShTerminalFrontendScope = project.service()
  }
}

package com.intellij.terminal.backend.hyperlinks.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.terminal.backend.hyperlinks.TerminalHyperlinksSessionsManager
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksRemoteApi
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionId

internal class TerminalHyperlinksRemoteApiImpl : TerminalHyperlinksRemoteApi {
  override suspend fun createNewSession(projectId: ProjectId): TerminalHyperlinksSessionId {
    val project = projectId.findProject()
    return TerminalHyperlinksSessionsManager.getInstance().createNewSession(project).id
  }

  override suspend fun closeSession(sessionId: TerminalHyperlinksSessionId) {
    return TerminalHyperlinksSessionsManager.getInstance().closeSession(sessionId)
  }
}
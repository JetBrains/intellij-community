package com.intellij.terminal.backend.hyperlinks.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.terminal.backend.hyperlinks.BackendTerminalHyperlinksSessionsManager
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionRemoteApi
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinkClickedEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksOutputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSession
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSessionId

internal class TerminalHyperlinksSessionRemoteApiImpl : TerminalHyperlinksSessionRemoteApi {
  override suspend fun getInputEventsSink(
    projectId: ProjectId,
    sessionId: TerminalHyperlinksSessionId,
  ): SendChannel<TerminalHyperlinksInputEvent> {
    return getSession(projectId, sessionId).inputEventsSink
  }

  override suspend fun getHyperlinkUpdatesChannel(
    projectId: ProjectId,
    sessionId: TerminalHyperlinksSessionId,
  ): ReceiveChannel<TerminalHyperlinksOutputEvent> {
    return getSession(projectId, sessionId).hyperlinkUpdatesChannel
  }

  override suspend fun handleHyperlinkClick(
    projectId: ProjectId,
    sessionId: TerminalHyperlinksSessionId,
    event: TerminalHyperlinkClickedEvent,
  ) {
    getSession(projectId, sessionId).handleHyperlinkClick(event)
  }

  private fun getSession(projectId: ProjectId, sessionId: TerminalHyperlinksSessionId): TerminalHyperlinksSession {
    val project = projectId.findProject()
    return BackendTerminalHyperlinksSessionsManager.getInstance(project).getSession(sessionId)
           ?: throw NoSuchElementException("Failed to find session with id: $sessionId")
  }
}
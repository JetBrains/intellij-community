package com.intellij.terminal.backend.hyperlinks.rpc

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
  override suspend fun getInputEventsSink(sessionId: TerminalHyperlinksSessionId): SendChannel<TerminalHyperlinksInputEvent> {
    return getSession(sessionId).inputEventsSink
  }

  override suspend fun getHyperlinkUpdatesChannel(sessionId: TerminalHyperlinksSessionId): ReceiveChannel<TerminalHyperlinksOutputEvent> {
    return getSession(sessionId).hyperlinkUpdatesChannel
  }

  override suspend fun handleHyperlinkClick(sessionId: TerminalHyperlinksSessionId, event: TerminalHyperlinkClickedEvent) {
    getSession(sessionId).handleHyperlinkClick(event)
  }

  private fun getSession(sessionId: TerminalHyperlinksSessionId): TerminalHyperlinksSession {
    return BackendTerminalHyperlinksSessionsManager.getInstance().getSession(sessionId)
           ?: throw NoSuchElementException("Failed to find session with id: $sessionId")
  }
}
package com.intellij.terminal.backend.hyperlinks.rpc

import com.intellij.terminal.backend.hyperlinks.TerminalHyperlinksSessionsManager
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksChangedEvent
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksSession
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionId
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionRemoteApi
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinkClickedEvent

internal class TerminalHyperlinksSessionRemoteApiImpl : TerminalHyperlinksSessionRemoteApi {
  override suspend fun getInputEventsSink(sessionId: TerminalHyperlinksSessionId): SendChannel<TerminalHyperlinksInputEvent> {
    return getSession(sessionId).inputEventsSink
  }

  override suspend fun getHyperlinkUpdatesChannel(sessionId: TerminalHyperlinksSessionId): ReceiveChannel<TerminalHyperlinksChangedEvent> {
    return getSession(sessionId).hyperlinkUpdatesChannel
  }

  override suspend fun handleHyperlinkClick(sessionId: TerminalHyperlinksSessionId, event: TerminalHyperlinkClickedEvent) {
    getSession(sessionId).handleHyperlinkClick(event)
  }

  private fun getSession(sessionId: TerminalHyperlinksSessionId): TerminalHyperlinksSession {
    return TerminalHyperlinksSessionsManager.getInstance().getSession(sessionId)
           ?: throw NoSuchElementException("Failed to find session with id: $sessionId")
  }
}
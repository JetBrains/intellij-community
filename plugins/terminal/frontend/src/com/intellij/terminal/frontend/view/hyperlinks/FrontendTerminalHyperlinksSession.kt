package com.intellij.terminal.frontend.view.hyperlinks

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksSession
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionId
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionRemoteApi
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinkClickedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinksChangedEvent

internal class FrontendTerminalHyperlinksSession(
  override val id: TerminalHyperlinksSessionId,
  override val inputEventsSink: SendChannel<TerminalHyperlinksInputEvent>,
  override val hyperlinkUpdatesChannel: ReceiveChannel<TerminalHyperlinksChangedEvent>,
) : TerminalHyperlinksSession {
  override suspend fun handleHyperlinkClick(event: TerminalHyperlinkClickedEvent) {
    TerminalHyperlinksSessionRemoteApi.getInstance().handleHyperlinkClick(id, event)
  }
}
package com.intellij.terminal.frontend.view.hyperlinks

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionRemoteApi
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinkClickedEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksOutputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSession
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSessionId

internal class FrontendTerminalHyperlinksSession(
  override val id: TerminalHyperlinksSessionId,
  override val inputEventsSink: SendChannel<TerminalHyperlinksInputEvent>,
  override val hyperlinkUpdatesChannel: ReceiveChannel<TerminalHyperlinksOutputEvent>,
) : TerminalHyperlinksSession {
  override suspend fun handleHyperlinkClick(event: TerminalHyperlinkClickedEvent) {
    TerminalHyperlinksSessionRemoteApi.getInstance().handleHyperlinkClick(id, event)
  }
}
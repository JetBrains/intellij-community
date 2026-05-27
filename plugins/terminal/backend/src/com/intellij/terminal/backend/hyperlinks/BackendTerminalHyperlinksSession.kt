package com.intellij.terminal.backend.hyperlinks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksSession
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionId
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinkClickedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinksChangedEvent

internal class BackendTerminalHyperlinksSession(
  override val id: TerminalHyperlinksSessionId,
  override val inputEventsSink: Channel<TerminalHyperlinksInputEvent>,
  override val hyperlinkUpdatesChannel: Channel<TerminalHyperlinksChangedEvent>,
  val hyperlinksFacade: BackendTerminalHyperlinkFacade,
  val coroutineScope: CoroutineScope,
) : TerminalHyperlinksSession {
  override suspend fun handleHyperlinkClick(event: TerminalHyperlinkClickedEvent) {
    hyperlinksFacade.hyperlinkClicked(event.hyperlinkId, event.mouseEvent)
  }
}
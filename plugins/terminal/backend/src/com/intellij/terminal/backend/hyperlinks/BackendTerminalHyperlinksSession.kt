package com.intellij.terminal.backend.hyperlinks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkClickedEvent
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksChangedEvent
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksSession
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionId

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
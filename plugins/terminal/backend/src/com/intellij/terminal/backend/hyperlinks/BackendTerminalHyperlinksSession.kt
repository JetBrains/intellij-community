package com.intellij.terminal.backend.hyperlinks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkClickedEvent
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksOutputEvent
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksSession
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionId

internal class BackendTerminalHyperlinksSession(
  override val id: TerminalHyperlinksSessionId,
  override val inputEventsSink: Channel<TerminalHyperlinksInputEvent>,
  override val hyperlinkUpdatesChannel: Channel<TerminalHyperlinksOutputEvent>,
  val hyperlinksFacade: BackendTerminalHyperlinkFacade,
  val coroutineScope: CoroutineScope,
) : TerminalHyperlinksSession {
  override suspend fun handleHyperlinkClick(event: TerminalHyperlinkClickedEvent) {
    hyperlinksFacade.hyperlinkClicked(event.hyperlinkId, event.mouseEvent)
  }
}
package com.intellij.terminal.backend.hyperlinks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinkClickedEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksOutputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSession
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSessionId

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
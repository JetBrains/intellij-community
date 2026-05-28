// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionId

@ApiStatus.Internal
interface TerminalHyperlinksSession {
  val id: TerminalHyperlinksSessionId

  val inputEventsSink: SendChannel<TerminalHyperlinksInputEvent>

  val hyperlinkUpdatesChannel: ReceiveChannel<TerminalHyperlinksChangedEvent>

  suspend fun handleHyperlinkClick(event: TerminalHyperlinkClickedEvent)
}
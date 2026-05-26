package com.intellij.terminal.frontend.session.hyperlinks

import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.hyperlinks.BackendHyperlinkInfo
import org.jetbrains.plugins.terminal.hyperlinks.BackendHyperlinkInfoService
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinkId

internal class BackendHyperlinkInfoServiceImpl : BackendHyperlinkInfoService {
  override fun getHyperlinkInfo(sessionId: TerminalSessionId, isAlternateBuffer: Boolean, hyperlinkId: TerminalHyperlinkId): BackendHyperlinkInfo? {
    // Find hyperlink in the model for the given session
    return null
  }
}

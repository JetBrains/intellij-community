package com.intellij.terminal.backend.hyperlinks

import org.jetbrains.plugins.terminal.hyperlinks.BackendHyperlinkInfo
import org.jetbrains.plugins.terminal.hyperlinks.BackendHyperlinkInfoService
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionId
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinkId

internal class BackendHyperlinkInfoServiceImpl : BackendHyperlinkInfoService {
  override fun getHyperlinkInfo(
    sessionId: TerminalHyperlinksSessionId,
    hyperlinkId: TerminalHyperlinkId,
  ): BackendHyperlinkInfo? {
    val session = TerminalHyperlinksSessionsManager.getInstance().getSession(sessionId) ?: return null
    return session.hyperlinksFacade.getHyperlink(hyperlinkId)
  }
}

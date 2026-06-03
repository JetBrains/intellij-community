package com.intellij.terminal.backend.hyperlinks

import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.menu.BackendHyperlinkInfo
import org.jetbrains.plugins.terminal.hyperlinks.menu.BackendHyperlinkInfoService
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSessionId

internal class BackendHyperlinkInfoServiceImpl : BackendHyperlinkInfoService {
  override fun getHyperlinkInfo(
    sessionId: TerminalHyperlinksSessionId,
    hyperlinkId: TerminalHyperlinkId,
  ): BackendHyperlinkInfo? {
    val session = BackendTerminalHyperlinksSessionsManager.getInstance().getSession(sessionId) ?: return null
    return session.hyperlinksFacade.getHyperlink(hyperlinkId)
  }
}

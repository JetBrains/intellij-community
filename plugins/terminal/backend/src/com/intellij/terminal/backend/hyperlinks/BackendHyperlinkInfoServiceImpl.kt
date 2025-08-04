package com.intellij.terminal.backend.hyperlinks

import com.intellij.terminal.backend.StateAwareTerminalSession
import com.intellij.terminal.backend.TerminalSessionsManager
import com.intellij.terminal.session.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.hyperlinks.BackendHyperlinkInfo
import org.jetbrains.plugins.terminal.hyperlinks.BackendHyperlinkInfoService

internal class BackendHyperlinkInfoServiceImpl : BackendHyperlinkInfoService {
  override fun getHyperlinkInfo(sessionId: TerminalSessionId, isAlternateBuffer: Boolean, hyperlinkId: TerminalHyperlinkId): BackendHyperlinkInfo? {
    val session = TerminalSessionsManager.getInstance().getSession(sessionId) as? StateAwareTerminalSession? ?: return null
    val hyperlinkFacade = session.getHyperlinkFacade(isAlternateBuffer) ?: return null
    return hyperlinkFacade.getHyperlink(hyperlinkId)
  }
}

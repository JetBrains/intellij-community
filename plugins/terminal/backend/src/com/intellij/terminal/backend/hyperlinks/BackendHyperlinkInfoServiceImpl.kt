package com.intellij.terminal.backend.hyperlinks

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.menu.BackendHyperlinkInfo
import org.jetbrains.plugins.terminal.hyperlinks.menu.BackendHyperlinkInfoService
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSessionId

internal class BackendHyperlinkInfoServiceImpl : BackendHyperlinkInfoService {
  override suspend fun getHyperlinkInfo(
    project: Project,
    sessionId: TerminalHyperlinksSessionId,
    hyperlinkId: TerminalHyperlinkId,
  ): BackendHyperlinkInfo? {
    val session = BackendTerminalHyperlinksSessionsManager.getInstance(project).getSession(sessionId) ?: return null
    return session.hyperlinksFacade.getHyperlink(hyperlinkId)
  }
}

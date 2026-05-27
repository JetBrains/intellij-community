package com.intellij.terminal.frontend.view.hyperlinks

import com.intellij.execution.impl.EditorTextDecorationApplier
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.session.impl.toTerminalId

internal class FrontendTerminalHyperlinkFacade(
  private val applier: EditorTextDecorationApplier,
) {
  fun getHoveredHyperlinkId(): TerminalHyperlinkId? {
    return applier.getHoveredHyperlink()?.id?.toTerminalId()
  }
}

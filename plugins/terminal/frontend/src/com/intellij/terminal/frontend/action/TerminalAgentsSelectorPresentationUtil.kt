// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentMode

@ApiStatus.Internal
object TerminalAgentsSelectorPresentationUtil {
  @Nls
  fun buildText(terminalAgent: TerminalAgent, mode: TerminalAgentMode): String {
    val primaryText = when (mode) {
      TerminalAgentMode.RUN -> terminalAgent.displayName
      TerminalAgentMode.INSTALL_AND_RUN -> terminalAgent.installActionText ?: terminalAgent.displayName
    }
    val secondaryText = terminalAgent.secondaryText ?: return primaryText
    val secondaryColor = "#${ColorUtil.toHex(NamedColorUtil.getInactiveTextColor())}"
    return HtmlChunk.html().children(
      HtmlChunk.text(primaryText),
      HtmlChunk.span("color: $secondaryColor").addText(" $secondaryText"),
    ).toString()
  }

  fun applyToPresentation(
    presentation: Presentation,
    terminalAgent: TerminalAgent,
    mode: TerminalAgentMode,
    showNewBadge: Boolean,
  ) {
    presentation.text = buildText(terminalAgent, mode)
    presentation.icon = terminalAgent.icon
    presentation.putClientProperty(ActionUtil.SECONDARY_TEXT, null)
    presentation.putClientProperty(ActionUtil.SECONDARY_ICON, if (showNewBadge) AllIcons.General.New_badge else null)
  }
}

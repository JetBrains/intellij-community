package com.intellij.terminal.frontend.action

import com.intellij.ide.actions.ToolWindowTabRenameActionBase
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

internal class TerminalRenameTabAction : ToolWindowTabRenameActionBase(
  TerminalToolWindowFactory.TOOL_WINDOW_ID,
  TerminalBundle.message("action.RenameSession.newSessionName.label")
), DumbAware {
  override fun getContentDisplayNameToEdit(content: Content, project: Project): String {
    val widget = TerminalToolWindowManager.findWidgetByContent(content) ?: return content.displayName
    return widget.terminalTitle.buildFullTitle()
  }

  override fun applyContentDisplayName(content: Content, project: Project, @Nls newContentName: String) {
    TerminalToolWindowManager.findWidgetByContent(content)?.terminalTitle?.change {
      userDefinedTitle = newContentName
    }
  }
}
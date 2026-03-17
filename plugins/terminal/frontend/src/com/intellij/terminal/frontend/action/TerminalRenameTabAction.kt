package com.intellij.terminal.frontend.action

import com.intellij.ide.actions.ToolWindowTabRenameActionBase
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.findTabByContent
import com.intellij.terminal.frontend.toolwindow.impl.getTitleText
import com.intellij.ui.content.Content
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.buildSettingsAwareFullTitle

internal class TerminalRenameTabAction : ToolWindowTabRenameActionBase(
  TerminalToolWindowFactory.TOOL_WINDOW_ID,
  TerminalBundle.message("action.RenameSession.newSessionName.label")
), DumbAware {
  override fun getContentDisplayNameToEdit(content: Content, project: Project): String {
    return getReworkedTerminalTitle(content, project)
           ?: getClassicTerminalTitle(content)
           ?: content.displayName
  }

  override fun applyContentDisplayName(content: Content, project: Project, @Nls newContentName: String) {
    val title = findTerminalTitle(content, project) ?: return
    title.change {
      userDefinedTitle = newContentName
    }
  }

  private fun findTerminalTitle(content: Content, project: Project): TerminalTitle? {
    val terminalView = TerminalToolWindowTabsManager.getInstance(project).findTabByContent(content)?.view
    val terminalWidget = TerminalToolWindowManager.findWidgetByContent(content)
    return terminalView?.title ?: terminalWidget?.terminalTitle
  }

  private fun getReworkedTerminalTitle(content: Content, project: Project): String? {
    val view = TerminalToolWindowTabsManager.getInstance(project).findTabByContent(content)?.view ?: return null
    return view.getTitleText()
  }

  private fun getClassicTerminalTitle(content: Content): String? {
    val widget = TerminalToolWindowManager.findWidgetByContent(content) ?: return null
    return widget.terminalTitle.buildSettingsAwareFullTitle()
  }
}
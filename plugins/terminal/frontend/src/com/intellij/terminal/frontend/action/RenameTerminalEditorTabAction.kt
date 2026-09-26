package com.intellij.terminal.frontend.action

import com.intellij.ide.actions.RenamePopup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabActionBase
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import javax.swing.JComponent

internal class RenameTerminalEditorTabAction : ToolWindowEditorTabActionBase(), DumbAware {
  override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content) {
    e.presentation.isEnabledAndVisible = toolWindow.id == TerminalToolWindowFactory.TOOL_WINDOW_ID &&
                                         isTerminalContent(content)
  }

  override fun actionPerformed(e: AnActionEvent, content: Content) {
    findTerminalTitle(content) ?: return

    val currentName = getTerminalContentDisplayNameToEdit(content)
    RenamePopup(TerminalBundle.message("action.RenameSession.newSessionName.label"), currentName) { newContentName ->
      applyTerminalContentDisplayName(content, newContentName)
    }.show(
      anchorComponent = (e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? JComponent) ?: content.component,
      disposable = content,
      focusBackComponent = content.preferredFocusableComponent ?: content.component,
      balloonPosition = Balloon.Position.below
    )
  }

  private fun isTerminalContent(content: Content): Boolean =
    content.getTerminalTab()?.view != null || TerminalToolWindowManager.findWidgetByContent(content) != null
}

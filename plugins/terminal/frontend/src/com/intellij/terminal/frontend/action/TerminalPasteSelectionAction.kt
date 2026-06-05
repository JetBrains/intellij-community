package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.terminalView
import com.intellij.terminal.frontend.toolwindow.impl.TerminalClipboard
import com.intellij.terminal.frontend.view.impl.TerminalOutputScrollingModel
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class TerminalPasteSelectionAction : TerminalPromotedDumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val view = e.terminalView ?: error("TerminalView is missing")
    val scrollingModel = e.terminalEditor?.getUserData(TerminalOutputScrollingModel.KEY)

    TerminalClipboard.pasteClipboardContent(view, scrollingModel, preferSystemSelection = true)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.terminalView != null && TerminalOptionsProvider.instance.pasteOnMiddleMouseButton
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.terminal.frontend.TerminalOutputScrollingModel
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.terminalInput
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.ui.getClipboardText
import org.jetbrains.plugins.terminal.block.ui.sanitizeLineSeparators
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class TerminalPasteSelectionAction : TerminalPromotedDumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val terminalInput = e.terminalInput ?: error("TerminalInput is missing in terminal editor")
    val text = getClipboardText(useSystemSelectionClipboardIfAvailable = true)
    if (text != null) {
      val sanitizedText = sanitizeLineSeparators(text)
      terminalInput.sendBracketedString(sanitizedText)
    }

    // Scroll to the cursor if the scrolling model is available in this editor.
    // It can be absent if it is the alternate buffer editor.
    val scrollingModel = e.terminalEditor?.getUserData(TerminalOutputScrollingModel.KEY)
    scrollingModel?.scrollToCursor(force = true)
  }

  override fun update(e: AnActionEvent) {
    val editor = e.terminalEditor
    e.presentation.isEnabledAndVisible = editor != null &&
                                         editor.isReworkedTerminalEditor &&
                                         TerminalOptionsProvider.instance.pasteOnMiddleMouseButton
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.terminal.frontend.TerminalInput
import com.intellij.terminal.frontend.TerminalOutputScrollingModel
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.terminalInput
import com.jediterm.terminal.TerminalOutputStream
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.ui.getClipboardText
import org.jetbrains.plugins.terminal.block.ui.sanitizeLineSeparators
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.outputController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.promptController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.selectionController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.simpleTerminalController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalFocusModel
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalSession

internal class TerminalPasteAction : TerminalPromotedDumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.terminalEditor as? EditorEx ?: return
    val input = e.terminalInput
    when {
      editor.isPromptEditor -> pasteIntoPrompt(e, e.dataContext)
      editor.isAlternateBufferEditor -> pasteIntoTerminalSession(e)
      editor.isOutputEditor -> {
        if (e.terminalSession?.model?.isCommandRunning == true) {
          pasteIntoTerminalSession(e)
        }
        else {
          // do not pass data context of output editor to prompt
          pasteIntoPrompt(e, dataContext = null)
        }
      }
      input != null -> {
        pasteIntoInput(input)

        // Scroll to the cursor if the scrolling model is available in this editor.
        // It can be absent if it is the alternate buffer editor.
        val scrollingModel = editor.getUserData(TerminalOutputScrollingModel.KEY)
        scrollingModel?.scrollToCursor(force = true)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val editor = e.terminalEditor
    e.presentation.isEnabledAndVisible = editor != null && (
      editor.isPromptEditor || editor.isOutputEditor || editor.isAlternateBufferEditor || // gen1
      editor.isOutputModelEditor || editor.isAlternateBufferModelEditor // gen2
                                                           )
  }

  private fun pasteIntoPrompt(e: AnActionEvent, dataContext: DataContext?) {
    val promptController = e.promptController ?: return
    e.terminalFocusModel?.focusPrompt()
    promptController.performPaste(dataContext)
  }

  private fun pasteIntoTerminalSession(e: AnActionEvent) {
    val session = e.terminalSession ?: error("No TerminalSession in the data context")

    // clear text and block selection if it is an output editor
    e.selectionController?.clearSelection()

    // clear text selection if it is an alternate buffer editor
    e.simpleTerminalController?.clearTextSelection()

    e.outputController?.scrollToBottom()
    doPasteIntoTerminalSession(session.terminalOutputStream)
  }

  private fun doPasteIntoTerminalSession(output: TerminalOutputStream) {
    val text = getClipboardText() ?: return
    if (text.isNotEmpty()) {
      output.sendString(text, false)
    }
  }

  private fun pasteIntoInput(input: TerminalInput) {
    val text = getClipboardText() ?: return
    val sanitizedText = sanitizeLineSeparators(text)
    input.sendBracketedString(sanitizedText)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
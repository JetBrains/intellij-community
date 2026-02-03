package com.intellij.terminal.frontend.action

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteAction
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class TerminalInsertInlineCompletionAction : TerminalPromotedDumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val editor = e.terminalEditor
    e.presentation.isEnabled =
      editor != null &&
      editor.isReworkedTerminalEditor &&
      !InlineCompletionSession.getOrNull(editor)?.context?.textToInsert().isNullOrEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.terminalEditor ?: return
    WriteAction.run<Throwable> {
      InlineCompletion.getHandlerOrNull(editor)?.insert()
    }
  }

  companion object {
    const val ACTION_ID: String = "Terminal.InsertInlineCompletion"
  }
}

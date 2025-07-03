package com.intellij.terminal.frontend.action

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor

internal class TerminalInsertInlineCompletionAction : EditorAction(InsertInlineCompletionHandler()), ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> =
    if (context.editor?.isReworkedTerminalEditor == true) listOf(this) else emptyList()

  class InsertInlineCompletionHandler : EditorActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
      WriteAction.run<Throwable> {
        InlineCompletion.getHandlerOrNull(editor)?.insert()
      }
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
      return editor.isReworkedTerminalEditor && !InlineCompletionSession.getOrNull(editor)?.context?.textToInsert().isNullOrEmpty()
    }
  }

  companion object {
    const val ACTION_ID: String = "Terminal.InsertInlineCompletion"
  }
}

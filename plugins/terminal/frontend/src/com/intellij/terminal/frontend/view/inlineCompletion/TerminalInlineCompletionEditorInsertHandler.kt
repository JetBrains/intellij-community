package com.intellij.terminal.frontend.view.inlineCompletion

import com.intellij.codeInsight.inline.completion.InlineCompletionEditorInsertHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.terminal.frontend.view.impl.TerminalInput
import com.intellij.terminal.frontend.view.impl.TerminalTypeAhead

internal class TerminalInlineCompletionEditorInsertHandler : InlineCompletionEditorInsertHandler {
  override fun insert(editor: Editor, textToInsert: String, offset: Int, file: PsiFile) {
    val terminalTypeAhead = editor.getUserData(TerminalTypeAhead.KEY) ?: return
    val terminalInput = editor.getUserData(TerminalInput.KEY) ?: return

    terminalTypeAhead.type(textToInsert)
    terminalInput.sendString(textToInsert)
  }

  override fun isApplicable(editor: Editor): Boolean =
    editor.getUserData(TerminalTypeAhead.KEY) != null &&
    editor.getUserData(TerminalInput.KEY) != null
}
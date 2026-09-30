package com.intellij.terminal.frontend.view.inlineCompletion

import com.intellij.codeInsight.inline.completion.InlineCompletionEditorInsertHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.terminal.frontend.view.impl.TerminalInput
import com.intellij.terminal.frontend.fus.TerminalCommandCompletionStatistics
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAhead
import org.jetbrains.plugins.terminal.block.reworked.TerminalUsageLocalStorage

internal class TerminalInlineCompletionEditorInsertHandler : InlineCompletionEditorInsertHandler {
  override fun insert(editor: Editor, textToInsert: String, offset: Int, file: PsiFile) {
    val terminalInput = editor.getUserData(TerminalInput.KEY) ?: return

    editor.getUserData(TerminalTypeAhead.KEY)?.type(textToInsert)
    terminalInput.sendString(textToInsert)
    TerminalUsageLocalStorage.getInstance().recordInlineCompletionAccepted()
    editor.getUserData(TerminalCommandCompletionStatistics.KEY)?.recordInlineInserted(textToInsert.length)
  }

  override fun isApplicable(editor: Editor): Boolean =
    editor.getUserData(TerminalInput.KEY) != null
}
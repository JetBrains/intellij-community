package com.intellij.terminal.frontend.inlineCompletion

import com.intellij.codeInsight.inline.completion.tooltip.InlineCompletionInsertActionIdResolver
import com.intellij.openapi.editor.Editor
import com.intellij.terminal.frontend.action.TerminalInsertInlineCompletionAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor

internal class TerminalInlineCompletionInsertActionIdResolver : InlineCompletionInsertActionIdResolver {
  override val actionId: String = TerminalInsertInlineCompletionAction.ACTION_ID
  override fun isApplicable(editor: Editor): Boolean = editor.isReworkedTerminalEditor
}
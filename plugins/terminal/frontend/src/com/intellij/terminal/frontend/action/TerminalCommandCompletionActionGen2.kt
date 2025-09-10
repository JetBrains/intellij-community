package com.intellij.terminal.frontend.action

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.actions.BaseCodeCompletionAction
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.terminal.frontend.completion.TerminalCommandCompletionHandler
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isSuppressCompletion
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class TerminalCommandCompletionActionGen2 : BaseCodeCompletionAction(), ActionPromoter {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.terminalEditor!!
    if (!editor.isSuppressCompletion) {
      val inlineCompletionHandler = InlineCompletion.getHandlerOrNull(editor)
      val inlineCompletionContext = InlineCompletionContext.getOrNull(editor)
      if (inlineCompletionHandler != null && inlineCompletionContext != null) {
        inlineCompletionHandler.hide(inlineCompletionContext)
      }
      val terminalCodeCompletion = TerminalCommandCompletionHandler(CompletionType.BASIC, true, false, true)
      terminalCodeCompletion.invokeCompletion(e, 1)
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.terminalEditor?.isOutputModelEditor == true && TerminalCommandCompletion.isEnabled()
  }

  override fun createHandler(
    completionType: CompletionType,
    invokedExplicitly: Boolean,
    autopopup: Boolean,
    synchronous: Boolean,
  ): CodeCompletionHandlerBase {
    return TerminalCommandCompletionHandler(completionType, invokedExplicitly, autopopup, synchronous)
  }

  override fun promote(actions: @Unmodifiable List<AnAction?>, context: DataContext): @Unmodifiable List<AnAction?>? {
    return listOf(this)
  }
}
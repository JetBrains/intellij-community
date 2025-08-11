package com.intellij.terminal.frontend.action

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.actions.BaseCodeCompletionAction
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.frontend.TerminalCommandCompletion
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner.Companion.REWORKED_TERMINAL_COMPLETION_POPUP
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
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
      val terminalCodeCompletion = TerminalCommandCompletion(CompletionType.BASIC, true, false, true)
      terminalCodeCompletion.invokeCompletion(e, 1)
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.terminalEditor?.isReworkedTerminalEditor == true && Registry.`is`(REWORKED_TERMINAL_COMPLETION_POPUP)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun createHandler(
    completionType: CompletionType,
    invokedExplicitly: Boolean,
    autopopup: Boolean,
    synchronous: Boolean,
  ): CodeCompletionHandlerBase {
    return TerminalCommandCompletion(completionType, invokedExplicitly, autopopup, synchronous)
  }

  override fun promote(actions: @Unmodifiable List<AnAction?>, context: DataContext): @Unmodifiable List<AnAction?>? {
    return listOf(this)
  }
}
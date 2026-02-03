package com.intellij.terminal.frontend.action

import com.intellij.codeInsight.completion.actions.BaseCodeCompletionAction
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.terminalView
import com.intellij.terminal.frontend.view.activeOutputModel
import com.intellij.terminal.frontend.view.completion.TerminalCommandCompletionService
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isSuppressCompletion
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor
import org.jetbrains.plugins.terminal.session.guessShellName
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus.TypingCommand

internal class TerminalCommandCompletionActionGen2 : BaseCodeCompletionAction(), ActionPromoter {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.terminalEditor ?: return
    val view = e.terminalView ?: return
    val shellIntegration = view.shellIntegrationDeferred.getNow() ?: return

    if (!editor.isSuppressCompletion) {
      val inlineCompletionHandler = InlineCompletion.getHandlerOrNull(editor)
      val inlineCompletionContext = InlineCompletionContext.getOrNull(editor)
      if (inlineCompletionHandler != null && inlineCompletionContext != null) {
        inlineCompletionHandler.hide(inlineCompletionContext)
      }

      TerminalCommandCompletionService.getInstance(project).invokeCompletion(
        view,
        editor,
        view.activeOutputModel(),
        shellIntegration,
        isAutoPopup = false
      )
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    val terminalView = e.terminalView
    val shellName = terminalView?.startupOptionsDeferred?.getNow()?.guessShellName()
    val isCommandTypingMode = terminalView?.shellIntegrationDeferred?.getNow()?.outputStatus?.value == TypingCommand
    e.presentation.isEnabledAndVisible = e.terminalEditor?.isOutputModelEditor == true
                                         && project != null && TerminalCommandCompletion.isEnabled(project)
                                         && shellName != null && TerminalCommandCompletion.isSupportedForShell(shellName)
                                         && isCommandTypingMode
  }

  override fun promote(actions: @Unmodifiable List<AnAction?>, context: DataContext): @Unmodifiable List<AnAction?>? {
    return listOf(this)
  }
}
package com.intellij.terminal.frontend.action

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.actions.BaseCodeCompletionAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.frontend.TerminalCommandCompletion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner.Companion.REWORKED_TERMINAL_COMPLETION_POPUP
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isSuppressCompletion

@ApiStatus.Internal
class TerminalCommandCompletionAction : BaseCodeCompletionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.editor!!
    if (!editor.isSuppressCompletion) {
      if (e.editor?.isReworkedTerminalEditor == true) {
        val terminalCodeCompletion = TerminalCommandCompletion(CompletionType.BASIC, true, false, true)
        terminalCodeCompletion.invokeCompletion(e, 1)
      }
      else {
        invokeCompletion(e, CompletionType.BASIC, 1)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.editor?.isPromptEditor == true
    if (e.editor?.isReworkedTerminalEditor == true && Registry.`is`(REWORKED_TERMINAL_COMPLETION_POPUP)) {
      e.presentation.isEnabledAndVisible = true
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
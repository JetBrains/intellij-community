package com.intellij.terminal.frontend.action

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.actions.BaseCodeCompletionAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isSuppressCompletion

internal class TerminalCommandCompletionAction : BaseCodeCompletionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.editor!!
    if (!editor.isSuppressCompletion) {
        invokeCompletion(e, CompletionType.BASIC, 1)
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.editor?.isPromptEditor == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
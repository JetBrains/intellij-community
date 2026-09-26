package com.intellij.terminal.frontend.action

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.actions.BaseCodeCompletionAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isSuppressCompletion
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class TerminalCommandCompletionAction : BaseCodeCompletionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    if (e.terminalEditor!!.isSuppressCompletion) return
    invokeCompletion(e, CompletionType.BASIC, 1)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.terminalEditor?.isPromptEditor == true //&& isAvailableInCurrentMode()
  }

  override fun getBehavior(): ActionRemoteBehavior? {
    return null
  }
}
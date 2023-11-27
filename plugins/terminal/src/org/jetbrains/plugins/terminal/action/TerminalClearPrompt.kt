// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.promptController

class TerminalClearPrompt : DumbAwareAction(), ActionPromoter, ActionRemoteBehaviorSpecification.Disabled {
  override fun actionPerformed(e: AnActionEvent) {
    e.promptController?.reset()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.editor?.isPromptEditor == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    // On Windows and Linux, clear prompt and copy text actions have the same shortcut - Ctrl + C.
    // And since copy text action enabled only when there is selected text, prioritize it.
    val copyTextAction = actions.find { it is TerminalCopyTextAction }
    return if (copyTextAction != null) {
      listOf(copyTextAction, this)
    }
    else listOf(this)
  }
}
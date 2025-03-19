// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.actions.BaseCodeCompletionAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor

@ApiStatus.Internal
class TerminalCommandCompletionAction : BaseCodeCompletionAction(), ActionRemoteBehaviorSpecification.Disabled {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.editor!!
    if (editor.getUserData(SUPPRESS_COMPLETION) != true) {
      invokeCompletion(e, CompletionType.BASIC, 1)
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.editor?.isPromptEditor == true
  }

  companion object {
    val SUPPRESS_COMPLETION: Key<Boolean> = Key.create("SUPPRESS_TERMINAL_COMPLETION")
  }
}

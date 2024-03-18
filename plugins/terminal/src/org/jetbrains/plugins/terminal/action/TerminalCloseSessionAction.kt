// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isAlternateBufferEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.promptController
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.terminalSession
import org.jetbrains.plugins.terminal.exp.TerminalPromotedDumbAwareAction

class TerminalCloseSessionAction : TerminalPromotedDumbAwareAction(), ActionRemoteBehaviorSpecification.Disabled {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.editor ?: return
    val isEmptyPrompt = editor.isPromptEditor && editor.document.textLength == 0
    val isInOutputWithEmptyPrompt = editor.isOutputEditor
                                    && e.promptController?.commandText?.isEmpty() == true
                                    && e.terminalSession?.model?.isCommandRunning == false
    if (!isEmptyPrompt && !isInOutputWithEmptyPrompt) {
      return
    }

    val session = e.terminalSession ?: return
    session.terminalStarterFuture.thenAccept {
      // send Ctrl+D to the terminal session to close it
      it?.sendString("\u0004", false)
    }
  }

  override fun update(e: AnActionEvent) {
    val editor = e.editor
    e.presentation.isEnabledAndVisible = editor != null && (editor.isPromptEditor || editor.isOutputEditor || editor.isAlternateBufferEditor)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
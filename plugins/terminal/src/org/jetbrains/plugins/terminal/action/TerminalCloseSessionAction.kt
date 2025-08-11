// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.promptController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalSession
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class TerminalCloseSessionAction : TerminalPromotedDumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.terminalEditor ?: return
    val promptModel = e.promptController?.model
    val isEmptyPrompt = editor.isPromptEditor && editor.document.textLength == promptModel?.commandStartOffset
    val isInOutputWithEmptyPrompt = editor.isOutputEditor
                                    && promptModel?.commandText?.isEmpty() == true
                                    && e.terminalSession?.model?.isCommandRunning == false
    if (!isEmptyPrompt && !isInOutputWithEmptyPrompt) {
      return
    }

    val session = e.terminalSession ?: return
      // send Ctrl+D to the terminal session to close it
    session.terminalOutputStream.sendString("\u0004", false)
  }

  override fun update(e: AnActionEvent) {
    val editor = e.terminalEditor
    e.presentation.isEnabledAndVisible = editor != null && (editor.isPromptEditor || editor.isOutputEditor || editor.isAlternateBufferEditor)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

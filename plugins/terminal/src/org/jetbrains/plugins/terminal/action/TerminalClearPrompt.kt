// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.block.prompt.clearCommandAndResetChangesHistory
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.promptController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalFocusModel
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalSession

internal class TerminalClearPrompt : DumbAwareAction(), ActionRemoteBehaviorSpecification.Disabled {
  override fun actionPerformed(e: AnActionEvent) {
    e.terminalFocusModel?.focusPrompt()
    e.promptController?.model?.clearCommandAndResetChangesHistory()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.terminalEditor?.isPromptEditor == true
                                         || e.terminalEditor?.isOutputEditor == true && e.terminalSession?.model?.isCommandRunning != true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.outputController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalFocusModel
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalSession
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction

internal class TerminalClearAction : TerminalPromotedDumbAwareAction(), ActionRemoteBehaviorSpecification.Disabled {
  override fun actionPerformed(e: AnActionEvent) {
    e.terminalFocusModel?.focusPrompt()
    e.outputController?.outputModel?.clearBlocks()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = (e.editor?.isPromptEditor == true || e.editor?.isOutputEditor == true)
                                         && e.terminalSession?.model?.isCommandRunning != true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

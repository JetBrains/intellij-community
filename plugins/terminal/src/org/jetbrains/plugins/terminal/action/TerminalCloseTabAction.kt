// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.ide.actions.CloseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class TerminalCloseTabAction : TerminalPromotedDumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val isVisible = e.terminalEditor?.isReworkedTerminalEditor == true
    e.presentation.isVisible = isVisible
    e.presentation.isEnabled = isVisible && e.getData(CloseAction.CloseTarget.KEY) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getData(CloseAction.CloseTarget.KEY)?.close()
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class TerminalSelectAllAction : TerminalPromotedDumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.terminalEditor?.isReworkedTerminalEditor == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.terminalEditor ?: return
    editor.selectionModel.setSelection(0, editor.document.textLength)
  }
}

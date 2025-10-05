// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.selectionController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalSession
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

internal class TerminalInterruptCommandAction : DumbAwareAction(TerminalBundle.message("action.Terminal.InterruptCommand.text")),
                                       ActionRemoteBehaviorSpecification.Disabled {
  init {
    shortcutSet = TerminalUiUtils.createSingleShortcutSet(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val session = e.terminalSession?.takeIf { it.model.isCommandRunning } ?: return
    e.selectionController?.clearSelection()
    // Send Ctrl+C
    session.terminalOutputStream.sendString("\u0003", false)
  }

  override fun update(e: AnActionEvent) {
    // Enable this action even if there is nothing to interrupt (command is not running).
    // It is needed because this action has the same shortcut with Copy actions on Window and Linux.
    // And we can't let EditorCopy action execute and select the line and the caret.
    e.presentation.isEnabledAndVisible = (e.terminalEditor?.isOutputEditor == true || e.terminalEditor?.isAlternateBufferEditor == true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

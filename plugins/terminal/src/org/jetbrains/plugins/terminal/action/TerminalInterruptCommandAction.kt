// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isAlternateBufferEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.terminalSession
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class TerminalInterruptCommandAction : DumbAwareAction(TerminalBundle.message("action.Terminal.InterruptCommand.text")),
                                       ActionRemoteBehaviorSpecification.Disabled {
  init {
    val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK)
    shortcutSet = CustomShortcutSet(keyStroke)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val session = e.terminalSession?.takeIf { it.model.isCommandRunning } ?: return
    session.terminalStarterFuture.thenAccept {
      // Send Ctrl+C
      it?.sendString("\u0003", false)
    }
  }

  override fun update(e: AnActionEvent) {
    // Enable this action even if there is nothing to interrupt (command is not running).
    // It is needed because this action has the same shortcut with Copy actions on Window and Linux.
    // And we can't let EditorCopy action execute and select the line and the caret.
    e.presentation.isEnabledAndVisible = (e.editor?.isOutputEditor == true || e.editor?.isAlternateBufferEditor == true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
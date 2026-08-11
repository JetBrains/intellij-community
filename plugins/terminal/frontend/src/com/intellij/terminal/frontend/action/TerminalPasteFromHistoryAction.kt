package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.editor.actions.PasteFromHistoryAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor
import javax.swing.JComponent

internal class TerminalPasteFromHistoryAction
  : PasteFromHistoryAction(), ActionPromoter, ActionRemoteBehaviorSpecification.Frontend {

  override fun update(e: AnActionEvent) {
    val editor = e.terminalEditor
    e.presentation.isEnabledAndVisible = editor != null && (
      editor.isPromptEditor || editor.isOutputEditor || editor.isAlternateBufferEditor || // gen1
      editor.isOutputModelEditor || editor.isAlternateBufferModelEditor                   // gen2
    )
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun performPaste(e: AnActionEvent, focusedComponent: JComponent, pasteSimple: Boolean) {
    val terminalPaste = ActionManager.getInstance().getAction("Terminal.Paste") ?: return
    val newEvent = AnActionEvent.createEvent(terminalPaste, e.dataContext, null, e.place, ActionUiKind.NONE, e.inputEvent)
    ActionUtil.performAction(terminalPaste, newEvent)
  }
}
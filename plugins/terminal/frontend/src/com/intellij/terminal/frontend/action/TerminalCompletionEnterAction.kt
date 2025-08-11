package com.intellij.terminal.frontend.action

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class TerminalCompletionEnterAction : TerminalPromotedDumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val lookup = LookupManager.getActiveLookup(e.terminalEditor) as LookupImpl?
    lookup?.finishLookup(Lookup.NORMAL_SELECT_CHAR)
  }

  override fun update(e: AnActionEvent) {
    val editor = e.terminalEditor
    if (editor == null || LookupManager.getActiveLookup(editor) == null || !editor.isOutputModelEditor) {
      e.presentation.isEnabled = false
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}
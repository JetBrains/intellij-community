// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.selectionController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

/** Can be invoked only from the Prompt */
internal class TerminalSelectLastBlockAction : TerminalPromotedDumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.selectLastBlock()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.terminalEditor?.isPromptEditor == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal abstract class TerminalOutputSelectionAction : TerminalPromotedDumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.terminalEditor?.isOutputEditor == true
                                         && e.selectionController?.primarySelection != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

/** Removes the selection and moves the focus to the prompt */
internal class TerminalSelectPromptAction : TerminalOutputSelectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.clearSelection()
  }
}

internal class TerminalSelectBlockBelowAction : TerminalOutputSelectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.selectRelativeBlock(isBelow = true, dropCurrentSelection = true)
  }
}

internal class TerminalSelectBlockAboveAction : TerminalOutputSelectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.selectRelativeBlock(isBelow = false, dropCurrentSelection = true)
  }
}

internal class TerminalExpandBlockSelectionBelowAction : TerminalOutputSelectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.selectRelativeBlock(isBelow = true, dropCurrentSelection = false)
  }
}

internal class TerminalExpandBlockSelectionAboveAction : TerminalOutputSelectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.selectRelativeBlock(isBelow = false, dropCurrentSelection = false)
  }
}

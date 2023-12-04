// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.selectionController

/** Can be invoked only from the Prompt */
class TerminalSelectLastBlockAction : TerminalPromotedDumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.selectLastBlock()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.editor?.isPromptEditor == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

abstract class TerminalOutputSelectionAction : TerminalPromotedDumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.editor?.isOutputEditor == true
                                         && e.selectionController?.primarySelection != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

/** Removes the selection and moves the focus to the prompt */
class TerminalSelectPromptAction : TerminalOutputSelectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.clearSelection()
  }
}

class TerminalSelectBlockBelowAction : TerminalOutputSelectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.selectRelativeBlock(isBelow = true, dropCurrentSelection = true)
  }
}

class TerminalSelectBlockAboveAction : TerminalOutputSelectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.selectRelativeBlock(isBelow = false, dropCurrentSelection = true)
  }
}

class TerminalExpandBlockSelectionBelowAction : TerminalOutputSelectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.selectRelativeBlock(isBelow = true, dropCurrentSelection = false)
  }
}

class TerminalExpandBlockSelectionAboveAction : TerminalOutputSelectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.selectRelativeBlock(isBelow = false, dropCurrentSelection = false)
  }
}
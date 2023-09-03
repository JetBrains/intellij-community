// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

/** Can be invoked only from the Prompt */
class TerminalSelectLastBlockAction : TerminalPromotedDumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.selectLastBlock()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.promptController != null && e.selectionController != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

abstract class TerminalOutputSelectionAction : TerminalPromotedDumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.outputController != null && e.selectionController != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/** Removes the selection and moves the focus to the prompt */
class TerminalSelectPromptAction : TerminalOutputSelectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.clearSelection()
  }
}

class TerminalSelectBlockBelowAction : TerminalOutputSelectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.selectRelativeBlock(isBelow = true)
  }
}

class TerminalSelectBlockAboveAction : TerminalOutputSelectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.selectionController?.selectRelativeBlock(isBelow = false)
  }
}

class TerminalSelectPromptHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    val selectionController = editor.getUserData(TerminalSelectionController.KEY)
    if (selectionController != null) {
      // clear selection to move the focus to the prompt
      selectionController.clearSelection()
    }
    else originalHandler.execute(editor, caret, dataContext)
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return editor.getUserData(TerminalOutputController.KEY) != null
           && editor.getUserData(TerminalSelectionController.KEY) != null
           || originalHandler.isEnabled(editor, caret, dataContext)
  }
}
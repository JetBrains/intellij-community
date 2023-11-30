// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.runWriteAction
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalPromotedDumbAwareAction

abstract class TerminalMoveCaretAction(private val moveToStart: Boolean) : TerminalPromotedDumbAwareAction(), ActionRemoteBehaviorSpecification.Disabled {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.editor ?: return
    val lineIndex = editor.offsetToLogicalPosition(editor.caretModel.offset).line
    val newOffset = if (moveToStart) {
      editor.document.getLineStartOffset(lineIndex)
    }
    else editor.document.getLineEndOffset(lineIndex)

    runWriteAction {
      editor.caretModel.moveToOffset(newOffset)
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.editor?.isPromptEditor == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class TerminalMoveCaretToLineStartAction : TerminalMoveCaretAction(moveToStart = true)

class TerminalMoveCaretToLineEndAction : TerminalMoveCaretAction(moveToStart = false)
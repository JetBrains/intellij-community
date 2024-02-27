// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.terminal.exp.TerminalPromotedEditorAction
import org.jetbrains.plugins.terminal.exp.TerminalUiUtils
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

private class TerminalMoveCaretActionHandler(private val moveToStart: Boolean) : TerminalPromptEditorActionHandler() {
  override fun executeAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val lineIndex = editor.offsetToLogicalPosition(editor.caretModel.offset).line
    val newOffset = if (moveToStart) {
      editor.document.getLineStartOffset(lineIndex)
    }
    else editor.document.getLineEndOffset(lineIndex)

    runWriteAction {
      editor.caretModel.moveToOffset(newOffset)
    }
  }
}

class TerminalMoveCaretToLineStartAction : TerminalPromotedEditorAction(TerminalMoveCaretActionHandler(moveToStart = true)),
                                           ActionRemoteBehaviorSpecification.Disabled {
  init {
    shortcutSet = TerminalUiUtils.createSingleShortcutSet(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK)
  }
}

class TerminalMoveCaretToLineEndAction : TerminalPromotedEditorAction(TerminalMoveCaretActionHandler(moveToStart = false)),
                                         ActionRemoteBehaviorSpecification.Disabled {
  init {
    shortcutSet = TerminalUiUtils.createSingleShortcutSet(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK)
  }
}
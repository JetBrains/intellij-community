// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.util.DocumentUtil
import org.jetbrains.plugins.terminal.exp.TerminalPromotedEditorAction

/**
 * Deletes the word before the caret position.
 * But the behavior depends on the char before the caret:
 * 1. If it is a letter or digit, then remove letters or digits until the delimiter.
 * 2. If it is a delimiter, then remove delimiters until a letter or digit and then remove letters or digits until the delimiter.
 */
class TerminalDeletePreviousWordAction : TerminalPromotedEditorAction(Handler()), ActionRemoteBehaviorSpecification.Disabled {
  private class Handler : TerminalPromptEditorActionHandler() {
    override fun executeAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
      val caretOffset = editor.caretModel.offset.takeIf { it > 0 } ?: return  // caret at the start, nothing to delete
      val text = editor.document.immutableCharSequence

      val delimiterIndex = if (text[caretOffset - 1].isLetterOrDigit()) {
        text.indexOfLastBefore(caretOffset) { !it.isLetterOrDigit() }
      }
      else {
        val letterOrDigitIndex = text.indexOfLastBefore(caretOffset) { it.isLetterOrDigit() }.coerceAtLeast(0)
        text.indexOfLastBefore(letterOrDigitIndex) { !it.isLetterOrDigit() }
      }

      DocumentUtil.writeInRunUndoTransparentAction {
        editor.document.deleteString(delimiterIndex + 1, caretOffset)
      }
    }

    private inline fun CharSequence.indexOfLastBefore(index: Int, predicate: (Char) -> Boolean): Int {
      return subSequence(0, index).indexOfLast(predicate)
    }
  }
}
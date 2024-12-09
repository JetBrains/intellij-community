// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.plugins.terminal.block.session.StyleRange

internal class TerminalModel(val editor: EditorEx) {
  private val document = editor.document

  private val mutableCaretOffsetState = MutableStateFlow(0)
  val caretOffsetState: StateFlow<Int> = mutableCaretOffsetState.asStateFlow()

  @RequiresEdt
  @RequiresWriteLock
  fun updateEditorContent(startLineIndex: Int, text: String, styles: List<StyleRange>) {
    CommandProcessor.getInstance().runUndoTransparentAction {
      doUpdateEditorContent(startLineIndex, text, styles)
    }
  }

  fun updateCaretPosition(logicalLineIndex: Int, columnIndex: Int) {
    val newOffset = editor.logicalPositionToOffset(LogicalPosition(logicalLineIndex, columnIndex))
    mutableCaretOffsetState.value = newOffset
  }

  private fun doUpdateEditorContent(startLineIndex: Int, text: String, styles: List<StyleRange>) {
    if (startLineIndex >= document.lineCount && document.textLength > 0) {
      val newLines = "\n".repeat(startLineIndex - document.lineCount + 1)
      document.insertString(document.textLength, newLines)
    }

    val replaceStartOffset = document.getLineStartOffset(startLineIndex)
    document.replaceString(replaceStartOffset, document.textLength, text)
  }
}
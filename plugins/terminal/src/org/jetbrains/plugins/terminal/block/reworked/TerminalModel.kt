// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.plugins.terminal.block.session.StyleRange

internal class TerminalModel(
  val editor: EditorEx,
  private val settings: JBTerminalSystemSettingsProviderBase,
) {
  private val document = editor.document

  private val mutableCaretOffsetState = MutableStateFlow(0)
  val caretOffsetState: StateFlow<Int> = mutableCaretOffsetState.asStateFlow()

  private val mutableTerminalStateFlow = MutableStateFlow(getInitialState())
  val terminalState: StateFlow<TerminalState> = mutableTerminalStateFlow.asStateFlow()

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

  fun updateTerminalState(state: TerminalState) {
    mutableTerminalStateFlow.value = state
  }

  private fun getInitialState(): TerminalState {
    return TerminalState(
      isCursorVisible = true,
      cursorShape = settings.cursorShape,
      mouseMode = MouseMode.MOUSE_REPORTING_NONE,
      mouseFormat = MouseFormat.MOUSE_FORMAT_XTERM,
      isAlternateScreenBuffer = false,
      isApplicationArrowKeys = false,
      isApplicationKeypad = false,
      isAutoNewLine = false,
      isAltSendsEscape = true,
      isBracketedPasteMode = false,
      windowTitle = ""
    )
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
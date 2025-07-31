// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.intellij.terminal.session.TerminalCursorPositionChangedEvent
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.util.CharUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.ui.getLengthWithoutDwc
import org.jetbrains.plugins.terminal.block.ui.withLock

@ApiStatus.Internal
class TerminalCursorPositionTracker(
  private val textBuffer: TerminalTextBuffer,
  private val discardedHistoryTracker: TerminalDiscardedHistoryTracker,
  terminalDisplay: TerminalDisplayImpl,
) {
  private var cursorX: Int = 0
  private var cursorY: Int = 0
  private var cursorPositionChanged: Boolean = false

  init {
    terminalDisplay.addListener(object : TerminalDisplayListener {
      override fun cursorPositionChanged(x: Int, y: Int) {
        textBuffer.withLock {
          cursorX = x
          cursorY = y
          cursorPositionChanged = true

          // Make sure that line with cursor is created in the text buffer.
          textBuffer.getLine(y)
        }
      }
    })
  }

  /**
   * Should be executed under [TerminalTextBuffer.lock]
   */
  fun getCursorPositionUpdate(): TerminalCursorPositionChangedEvent? {
    return if (cursorPositionChanged) {
      doGetCursorPositionUpdate()
    }
    else null
  }

  private fun doGetCursorPositionUpdate(): TerminalCursorPositionChangedEvent? {
    check(cursorPositionChanged) { "It is expected that this method is called only if something is changed" }

    var line = cursorY
    // Lines in the terminal buffer contain special character DWC (double width character)
    // that indicate that the previous character is double width (for example, chinese symbol).
    // This character is synthetic and present there only to create space in the TextBuffer grid.
    // But DWC is dropped when we parse the TextBuffer to string, so we also should exclude them from the offset calculation there.
    val text = textBuffer.getLine(line).text
    // The cursorX value can be temporarily out of range due to async updates (or a bug in model state update).
    val end = cursorX.coerceIn(0, text.length)
    val dwcCountBeforeCursor = text.subSequence(0, end).count { it == CharUtils.DWC }
    var column = cursorX - dwcCountBeforeCursor

    // Ensure that line is either not a wrapped line or the start of the wrapped line.
    while (line - 1 >= -textBuffer.historyLinesCount && textBuffer.getLine(line - 1).isWrapped) {
      line--
      column += textBuffer.getLine(line).getLengthWithoutDwc()
    }

    val logicalLine = textBuffer.getLogicalLineIndex(line) + discardedHistoryTracker.getDiscardedLogicalLinesCount()

    cursorPositionChanged = false

    return TerminalCursorPositionChangedEvent(logicalLine, column)
  }
}
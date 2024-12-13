// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.output

import com.jediterm.terminal.model.TerminalTextBuffer

internal class TerminalCursorPositionTracker(
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
        cursorX = x
        cursorY = y
        cursorPositionChanged = true
      }
    })
  }

  fun getCursorPositionUpdate(): TerminalCursorPositionChangedEvent? {
    return if (cursorPositionChanged) {
      doGetCursorPositionUpdate()
    }
    else null
  }

  private fun doGetCursorPositionUpdate(): TerminalCursorPositionChangedEvent {
    check(cursorPositionChanged) { "It is expected that this method is called only if something is changed" }

    var line = cursorY
    var column = cursorX

    // Ensure that line is either not a wrapped line or the start of the wrapped line.
    while (line - 1 >= -textBuffer.historyLinesCount && textBuffer.getLine(line - 1).isWrapped) {
      line--
      column += textBuffer.getLine(line).length()
    }

    val logicalLine = textBuffer.getLogicalLineIndex(line) + discardedHistoryTracker.getDiscardedLogicalLinesCount()

    cursorPositionChanged = false

    return TerminalCursorPositionChangedEvent(logicalLine, column)
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.output

import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.model.TextBufferChangesListener

internal class TerminalDiscardedHistoryTracker(private val textBuffer: TerminalTextBuffer) {
  private var discardedLogicalLinesCount: Int = 0

  fun getDiscardedLogicalLinesCount(): Int {
    return if (textBuffer.isUsingAlternateBuffer) 0 else discardedLogicalLinesCount
  }

  init {
    textBuffer.addChangesListener(object : TextBufferChangesListener {
      override fun linesDiscardedFromHistory(lines: List<TerminalLine>) {
        if (textBuffer.isUsingAlternateBuffer) {
          return
        }

        for (line in lines) {
          if (!line.isWrapped) {
            discardedLogicalLinesCount++
          }
        }
      }

      override fun historyCleared() {
        // Reset to the initial state if history was cleared
        if (!textBuffer.isUsingAlternateBuffer) {
          discardedLogicalLinesCount = 0
        }
      }
    })
  }
}
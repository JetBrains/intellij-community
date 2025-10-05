// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.model.TextBufferChangesListener
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TerminalDiscardedHistoryTracker(private val textBuffer: TerminalTextBuffer) {
  private var discardedLogicalLinesCount: Long = 0L

  fun getDiscardedLogicalLinesCount(): Long {
    return if (textBuffer.isUsingAlternateBuffer) 0L else discardedLogicalLinesCount
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
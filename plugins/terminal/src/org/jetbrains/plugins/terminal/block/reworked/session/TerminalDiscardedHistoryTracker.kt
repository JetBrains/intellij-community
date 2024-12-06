// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.model.TextBufferChangesListener

internal class TerminalDiscardedHistoryTracker(textBuffer: TerminalTextBuffer) {
  var discardedLogicalLinesCount: Int = 0
    private set

  init {
    textBuffer.addChangesListener(object : TextBufferChangesListener {
      override fun linesDiscardedFromHistory(lines: List<TerminalLine>) {
        for (line in lines) {
          if (!line.isWrapped) {
            discardedLogicalLinesCount++
          }
        }
      }
    })
  }
}
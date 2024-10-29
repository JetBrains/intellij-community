// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session.scraper

import com.jediterm.terminal.model.LinesBuffer
import com.jediterm.terminal.model.TerminalLine

internal interface TerminalLinesCollector {
  fun addLines(linesBuffer: LinesBuffer) {
    for (i in 0 until linesBuffer.lineCount) {
      addLine(linesBuffer.getLine(i))
    }
  }

  fun addLine(line: TerminalLine)

  fun flush() {}

}

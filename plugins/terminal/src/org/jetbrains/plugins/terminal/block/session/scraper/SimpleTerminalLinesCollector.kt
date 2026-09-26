// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session.scraper

import com.jediterm.terminal.model.TerminalLine
import org.jetbrains.plugins.terminal.block.ui.normalize

internal class SimpleTerminalLinesCollector(
  private val delegate: StringCollector,
) : TerminalLinesCollector {

  override fun addLine(line: TerminalLine) {
    line.forEachEntry { entry ->
      val text = entry.text.normalize()
      if (text.isNotEmpty() && !entry.isNul) {
        delegate.write(text)
      }
    }
    if (!line.isWrapped) {
      delegate.newline()
    }
  }

  override fun flush() {
    delegate.buildText()
  }

}
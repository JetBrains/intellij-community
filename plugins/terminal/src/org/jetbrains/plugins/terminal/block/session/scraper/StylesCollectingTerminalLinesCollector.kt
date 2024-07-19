// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session.scraper

import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.TerminalLine
import org.jetbrains.plugins.terminal.block.session.StyleRange
import org.jetbrains.plugins.terminal.block.ui.normalize

internal class StylesCollectingTerminalLinesCollector(
  private val delegate: StringCollector,
  private val stylesConsumer: (StyleRange) -> Unit,
) : TerminalLinesCollector {

  override fun addLine(line: TerminalLine) {
    line.forEachEntry { entry ->
      val text = entry.text.normalize()
      if (text.isNotEmpty() && !entry.isNul) {
        delegate.write(text)
        if (entry.style != TextStyle.EMPTY) {
          val endOffset = delegate.length()
          val startOffset = endOffset - text.length
          val style = StyleRange(startOffset, endOffset, entry.style)
          stylesConsumer(style)
        }
      }
    }
    if (!line.isWrapped) {
      delegate.newline()
    }
  }

}
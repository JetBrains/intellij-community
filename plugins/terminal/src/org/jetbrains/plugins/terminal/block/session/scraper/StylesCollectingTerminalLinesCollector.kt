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
  private var previousLineWrapped: Boolean = true

  override fun addLine(line: TerminalLine) {
    // Add line break only if the previous line is not wrapped and we received an additional line
    if (!previousLineWrapped) {
      delegate.newline()
    }

    line.forEachEntry { entry ->
      val text = entry.text.normalize()
      if (text.isNotEmpty() && (!entry.isNul || entry.style != TextStyle.EMPTY)) {
        val nonNullText = if (entry.isNul) " ".repeat(text.length) else text
        delegate.write(nonNullText)
        if (entry.style != TextStyle.EMPTY) {
          val endOffset = delegate.length()
          val startOffset = endOffset - nonNullText.length
          val style = StyleRange(startOffset, endOffset, entry.style)
          stylesConsumer(style)
        }
      }
    }

    previousLineWrapped = line.isWrapped
  }

}
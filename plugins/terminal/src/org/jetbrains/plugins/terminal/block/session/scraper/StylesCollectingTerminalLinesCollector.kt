// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session.scraper

import com.jediterm.terminal.HyperlinkStyle
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.TerminalLine
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.block.ui.normalize
import org.jetbrains.plugins.terminal.session.impl.JediTermOsc8LinkInfo
import org.jetbrains.plugins.terminal.session.impl.Osc8Hyperlink
import org.jetbrains.plugins.terminal.session.impl.StyleRange

@ApiStatus.Internal
class StylesCollectingTerminalLinesCollector(
  private val delegate: StringCollector,
  private val stylesConsumer: (StyleRange) -> Unit,
  /**
   * Consumes OSC8 hyperlinks found in the output. Cells belonging to
   * a single OSC8 link are merged into one span, including across wrapped lines.
   */
  private val osc8HyperlinkConsumer: (Osc8Hyperlink) -> Unit = {},
) : TerminalLinesCollector {
  private var previousLineWrapped: Boolean = true

  // Current OSC8 link accumulating adjacent entries.
  private var currentHyperlink: MutableOsc8Hyperlink? = null

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
        val endOffset = delegate.length()
        val startOffset = endOffset - nonNullText.length
        if (entry.style != TextStyle.EMPTY) {
          val ignoreContrastAdjustment = !entry.isNul && text.any { TerminalUiUtils.shouldIgnoreContrastAdjustment(it) }
          val style = StyleRange(startOffset.toLong(), endOffset.toLong(), entry.style, ignoreContrastAdjustment)
          stylesConsumer(style)
        }
        processOsc8Hyperlink(entry.style, startOffset, endOffset)
      }
    }

    previousLineWrapped = line.isWrapped
  }

  /**
   * Finishes the current OSC8 link, if any. Must be called after the last [addLine].
   */
  override fun flush() {
    finishCurrentHyperlink()
  }

  private fun processOsc8Hyperlink(style: TextStyle, startOffset: Int, endOffset: Int) {
    val linkInfo = (style as? HyperlinkStyle)?.linkInfo as? JediTermOsc8LinkInfo
    if (currentHyperlink?.extend(linkInfo, startOffset, endOffset) != true) {
      finishCurrentHyperlink()
      if (linkInfo != null) {
        currentHyperlink = MutableOsc8Hyperlink(linkInfo, startOffset, endOffset)
      }
    }
  }

  private fun finishCurrentHyperlink() {
    val hyperlink = currentHyperlink ?: return
    osc8HyperlinkConsumer(Osc8Hyperlink(hyperlink.startOffset.toLong(), hyperlink.endOffset.toLong(), hyperlink.linkInfo.uri))
    currentHyperlink = null
  }

  private class MutableOsc8Hyperlink(
    val linkInfo: JediTermOsc8LinkInfo,
    val startOffset: Int,
    var endOffset: Int,
  ) {
    fun extend(nextLinkInfo: JediTermOsc8LinkInfo?, nextLinkStartOffset: Int, nextLinkEndOffset: Int): Boolean {
      if (linkInfo == nextLinkInfo && endOffset == nextLinkStartOffset) {
        // Same URI and the links are adjacent: extend the current link.
        endOffset = nextLinkEndOffset
        return true
      }
      return false
    }
  }
}

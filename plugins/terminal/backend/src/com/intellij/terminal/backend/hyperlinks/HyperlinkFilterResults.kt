// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend.hyperlinks

import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.HypertextInput
import com.intellij.execution.impl.InlayProvider
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalFilterResultInfoDto
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHighlightingInfoDto
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinkInfoDto
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalInlayInfoDto
import org.jetbrains.plugins.terminal.hyperlinks.session.toDto
import java.util.concurrent.atomic.AtomicLong

/**
 * Converts this result item into DTOs, taking fresh ids from [hyperlinkId].
 *
 * An item yields a hyperlink or a highlighting when it has one, and an inlay when it is
 * an [InlayProvider].
 *
 * @param absoluteOffsetOf maps an offset in the filtered text to an absolute terminal offset
 */
internal fun Filter.ResultItem.toFilterResultDtos(
  hyperlinkId: AtomicLong,
  absoluteOffsetOf: (Int) -> Long,
): List<TerminalFilterResultInfoDto> {
  val hyperlinkInfo = this.hyperlinkInfo
  val highlightAttributes = this.highlightAttributes
  val notInlayResult = when {
    hyperlinkInfo != null -> TerminalHyperlinkInfoDto(
      id = TerminalHyperlinkId(hyperlinkId.incrementAndGet()),
      hyperlinkInfo = hyperlinkInfo,
      absoluteStartOffset = absoluteOffsetOf(highlightStartOffset),
      absoluteEndOffset = absoluteOffsetOf(highlightEndOffset),
      style = highlightAttributes?.toDto(),
      followedStyle = followedHyperlinkAttributes?.toDto(),
      hoveredStyle = hoveredHyperlinkAttributes?.toDto(),
      isInvisibleLink = isInvisibleLink,
      layer = highlighterLayer,
    )
    highlightAttributes != null -> TerminalHighlightingInfoDto(
      id = TerminalHyperlinkId(hyperlinkId.incrementAndGet()),
      absoluteStartOffset = absoluteOffsetOf(highlightStartOffset),
      absoluteEndOffset = absoluteOffsetOf(highlightEndOffset),
      style = highlightAttributes.toDto(),
      layer = highlighterLayer,
    )
    else -> null
  }
  val inlayResult = (this as? InlayProvider)?.let { inlayProvider ->
    TerminalInlayInfoDto(
      id = TerminalHyperlinkId(hyperlinkId.incrementAndGet()),
      absoluteStartOffset = absoluteOffsetOf(highlightStartOffset),
      absoluteEndOffset = absoluteOffsetOf(highlightEndOffset),
      inlayProvider = inlayProvider,
    )
  }
  return listOfNotNull(notInlayResult, inlayResult)
}

/**
 * A [HypertextInput] over an immutable text with `'\n'` line separators.
 *
 * Every returned line text ends with `'\n'`, the last one included.
 */
internal class HypertextFromCharSequenceAdapter(private val chars: CharSequence) : HypertextInput {
  private val lineStartOffsets: IntArray = run {
    val lineCount = chars.count { it == '\n' } + 1
    val starts = IntArray(lineCount)
    var idx = 1
    chars.forEachIndexed { i, c ->
      if (c == '\n') {
        starts[idx++] = i + 1
      }
    }
    starts
  }

  override val lineCount: Int
    get() = lineStartOffsets.size

  override fun getLineStartOffset(lineIndex: Int): Int = lineStartOffsets[lineIndex]

  override fun getLineText(lineIndex: Int): String {
    val start = lineStartOffsets[lineIndex]
    return if (lineIndex + 1 < lineStartOffsets.size) {
      val end = lineStartOffsets[lineIndex + 1]
      chars.subSequence(start, end).toString()  // with line break at the end
    }
    else {
      chars.subSequence(start, chars.length).toString() + "\n"
    }
  }
}

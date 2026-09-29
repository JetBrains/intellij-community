// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.hyperlinks

import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputOsc8Hyperlink

internal object Osc8HyperlinkUtil {
  /**
   * Merges the parts of a URL that is split over several lines.
   *
   * A TUI app can wrap a long URL with hard line breaks and emit each line as a separate OSC8 link.
   *
   * Neighboring links are the parts of one URL when all of these hold:
   * - they have the same URI;
   * - only whitespace with exactly one line break separates each two of them;
   * - their texts joined together are equal to the URI.
   *
   * Other links stay as they are.
   *
   * @param parts the OSC8 links of [outputModel] sorted by offset, as
   * [TerminalOutputModel.getOsc8Hyperlinks] returns them.
   */
  fun mergeSplitUrls(outputModel: TerminalOutputModel, parts: List<TerminalOutputOsc8Hyperlink>): List<TerminalOutputOsc8Hyperlink> {
    if (parts.size < 2) return parts
    val result = ArrayList<TerminalOutputOsc8Hyperlink>(parts.size)
    var i = 0
    while (i < parts.size) {
      val partCount = countPartsOfSplitUrl(outputModel, parts, i)
      val part = parts[i]
      result.add(if (partCount == 1) part else part.copy(endOffset = parts[i + partCount - 1].endOffset))
      i += partCount
    }
    return result
  }

  /**
   * Returns the number of parts of the split URL that starts at [startPartIndex],
   * or 1 if no split URL starts there.
   */
  private fun countPartsOfSplitUrl(
    outputModel: TerminalOutputModel,
    parts: List<TerminalOutputOsc8Hyperlink>,
    startPartIndex: Int,
  ): Int {
    val partCount = countCandidateParts(parts, startPartIndex)
    val found = partCount > 1 && isSplitUrl(outputModel, parts, startPartIndex, partCount)
    return if (found) partCount else 1
  }

  /**
   * Returns the number of links from [startPartIndex] that have the same URI
   * and together have the length of that URI. Returns 1 if the lengths never match.
   *
   * The check reads no text, so it is cheap. [isSplitUrl] then checks the text
   * of the candidate parts.
   */
  private fun countCandidateParts(parts: List<TerminalOutputOsc8Hyperlink>, startPartIndex: Int): Int {
    val uri = parts[startPartIndex].uri
    var joinedLength = parts[startPartIndex].length
    var count = 1
    while (joinedLength < uri.length && startPartIndex + count < parts.size) {
      val nextPart = parts[startPartIndex + count]
      if (nextPart.uri != uri) return 1
      joinedLength += nextPart.length
      count++
    }
    return if (joinedLength == uri.length.toLong()) count else 1
  }

  /**
   * Returns `true` if the [partCount] links from [startPartIndex] form one split URL:
   * only whitespace with exactly one line break separates each two of them,
   * and their texts joined together are equal to the URI.
   */
  private fun isSplitUrl(
    outputModel: TerminalOutputModel,
    parts: List<TerminalOutputOsc8Hyperlink>,
    startPartIndex: Int,
    partCount: Int,
  ): Boolean {
    val uri = parts[startPartIndex].uri
    var uriOffset = 0
    for (i in startPartIndex until startPartIndex + partCount) {
      val part = parts[i]
      if (i > startPartIndex && !isLineBreakGap(outputModel.getText(parts[i - 1].endOffset, part.startOffset))) {
        return false
      }
      val partText = outputModel.getText(part.startOffset, part.endOffset)
      if (!uri.regionMatches(uriOffset, partText, 0, partText.length)) return false
      uriOffset += partText.length
    }
    return uriOffset == uri.length
  }

  private fun isLineBreakGap(gap: CharSequence): Boolean = gap.isBlank() && gap.count { it == '\n' } == 1

  private val TerminalOutputOsc8Hyperlink.length: Long
    get() = endOffset - startOffset
}

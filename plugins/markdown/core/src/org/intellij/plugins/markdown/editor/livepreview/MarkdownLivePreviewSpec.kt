// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

/** A live-preview decoration and the source range it reveals when touched. */
@ApiStatus.Internal
sealed interface MarkdownLivePreviewSpec {
  val range: TextRange

  /** Conceals one or more markup ranges, optionally replacing them with a placeholder. */
  data class Conceal(
    override val range: TextRange,
    val conceals: List<TextRange>,
  ) : MarkdownLivePreviewSpec

  /** Conceals a full logical line and paints it as a horizontal rule. */
  data class HorizontalRule(override val range: TextRange) : MarkdownLivePreviewSpec

  /** Replaces a list marker with a depth-aware bullet placeholder. */
  data class Bullet(
    override val range: TextRange,
    val concealRange: TextRange,
    val placeholderText: String,
  ) : MarkdownLivePreviewSpec
}

/** Everything live preview wants to hide in one state of a document. */
@ApiStatus.Internal
class MarkdownLivePreviewSpecSet(
  @JvmField val documentStamp: Long,
  @JvmField val elements: List<MarkdownLivePreviewSpec>,
) {
  @JvmField
  val maxElementLength: Int = elements.maxOfOrNull { it.range.length } ?: 0

  /** Indices of specs whose ranges intersect the closed interval [[start], [end]]. */
  fun intersecting(start: Int, end: Int, into: MutableSet<Int>) {
    // `elements` is sorted by start offset, so every element that intersects [start, end] starts at or
    // before `end`, and - since it also has to reach `start` - no earlier than `start - maxElementLength`.
    var index = firstElementAfter(end)
    val lowestStart = start - maxElementLength
    while (index-- > 0) {
      val element = elements[index]
      if (element.range.startOffset < lowestStart) break
      if (element.range.endOffset >= start) {
        into.add(index)
      }
    }
  }

  /** Index of the first element starting strictly after [offset], i.e., the exclusive upper bound. */
  private fun firstElementAfter(offset: Int): Int {
    var low = 0
    var high = elements.size
    while (low < high) {
      val mid = (low + high) ushr 1
      if (elements[mid].range.startOffset > offset) high = mid else low = mid + 1
    }
    return low
  }
}

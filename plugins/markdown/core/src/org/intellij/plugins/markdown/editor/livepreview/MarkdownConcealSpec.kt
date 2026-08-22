// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

/**
 * One inline element (`**bold**`, `` `code` ``, `[title](url)`, ...) together with the markup inside it
 * that live preview hides.
 *
 * [range] covers the whole element, including markers, and is the range that controls revealing: while a
 * caret or selection touches it, none of the [conceals] is hidden. Because the range covers the markers,
 * a caret sitting exactly at either edge of the element already reveals it, which is what keeps a fold
 * from ever ending up under a caret.
 *
 * Nested elements are separate entries, so revealing an inner element automatically reveals its ancestors:
 * an inner element's range is contained in the outer one's, so any caret touching the inner touches the
 * outer as well.
 */
@ApiStatus.Internal
data class MarkdownConcealElement(
  val range: TextRange,
  val conceals: List<TextRange>,
)

/**
 * Everything live preview wants to hide in one state of a document.
 *
 * [elements] is sorted by [MarkdownConcealElement.range] start offset and [maxElementLength] is the
 * longest element in it, which together let the reconciler binary search for the elements around a caret
 * instead of scanning the whole file on every caret movement.
 *
 * [documentStamp] is the [com.intellij.openapi.editor.Document.getModificationStamp] the specs were
 * computed from. The reconciler applies a set only while the stamp still matches the document.
 */
@ApiStatus.Internal
class MarkdownConcealSpecSet(
  @JvmField val documentStamp: Long,
  @JvmField val elements: List<MarkdownConcealElement>,
) {
  @JvmField
  val maxElementLength: Int = elements.maxOfOrNull { it.range.length } ?: 0

  /**
   * Indices of the elements whose [MarkdownConcealElement.range] intersects the closed interval
   * [[start], [end]]. Both ends are inclusive on purpose, see [MarkdownConcealElement.range].
   */
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

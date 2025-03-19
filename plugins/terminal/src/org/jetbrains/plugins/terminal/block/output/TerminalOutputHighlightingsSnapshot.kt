// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TerminalOutputHighlightingsSnapshot(private val document: Document, highlightings: List<HighlightingInfo>) {
  private val allSortedHighlightings: List<HighlightingInfo> = buildAndSortHighlightings(document, highlightings)

  val size: Int
    get() = allSortedHighlightings.size

  operator fun get(index: Int): HighlightingInfo = allSortedHighlightings[index]

  /**
   * @return index of a highlighting containing the `documentOffset` (`highlighting.startOffset <= documentOffset < highlighting.endOffset`).
   *         If no such highlighting is found:
   *           - returns 0 for negative `documentOffset`
   *           - total count of highlightings for `documentOffset >= document.textLength`
   */
  fun findHighlightingIndex(documentOffset: Int): Int {
    if (documentOffset <= 0) return 0
    val binarySearchInd = allSortedHighlightings.binarySearch(0, allSortedHighlightings.size) {
      it.startOffset.compareTo(documentOffset)
    }
    return if (binarySearchInd >= 0) binarySearchInd
    else {
      val insertionIndex = -binarySearchInd - 1
      if (insertionIndex == 0 || insertionIndex == allSortedHighlightings.size && documentOffset >= document.textLength) {
        insertionIndex
      }
      else {
        insertionIndex - 1
      }
    }
  }

  private fun buildAndSortHighlightings(document: Document, highlightings: List<HighlightingInfo>): List<HighlightingInfo> {
    val sortedHighlightings = highlightings.sortedBy { it.startOffset }
    val documentLength = document.textLength
    val result: MutableList<HighlightingInfo> = ArrayList(sortedHighlightings.size * 2 + 1)
    var startOffset = 0
    for (highlighting in sortedHighlightings) {
      if (highlighting.startOffset < 0 || highlighting.endOffset > documentLength) {
        logger<TerminalOutputModel>().error("Terminal highlightings range should be within document")
      }
      if (startOffset > highlighting.startOffset) {
        logger<TerminalOutputModel>().error("Terminal highlightings should not overlap")
      }
      if (startOffset < highlighting.startOffset) {
        result.add(HighlightingInfo(startOffset, highlighting.startOffset, EmptyTextAttributesProvider))
      }
      result.add(highlighting)
      startOffset = highlighting.endOffset
    }
    if (startOffset < documentLength) {
      result.add(HighlightingInfo(startOffset, documentLength, EmptyTextAttributesProvider))
    }
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TerminalOutputHighlightingsSnapshot

    return allSortedHighlightings == other.allSortedHighlightings
  }

  override fun hashCode(): Int {
    return allSortedHighlightings.hashCode()
  }

  override fun toString(): String {
    return "TerminalOutputHighlightingsSnapshot(allSortedHighlightings=$allSortedHighlightings)"
  }
}

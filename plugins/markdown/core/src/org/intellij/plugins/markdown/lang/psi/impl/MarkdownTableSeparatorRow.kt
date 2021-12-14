// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.parents
import org.intellij.plugins.markdown.editor.tables.TableProps
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.intellij.plugins.markdown.util.hasType

class MarkdownTableSeparatorRow(text: CharSequence): LeafPsiElement(MarkdownTokenTypes.TABLE_SEPARATOR, text), MarkdownPsiElement {
  private val cachedCellsRanges
    get() = CachedValuesManager.getCachedValue(this) {
      CachedValueProvider.Result.create(buildCellsRanges(), PsiModificationTracker.MODIFICATION_COUNT)
    }

  private fun buildCellsRanges(): List<TextRange> {
    val elementText = text
    val cells = elementText.split(TableProps.SEPARATOR_CHAR).toMutableList()
    val startedWithSeparator = cells.firstOrNull()?.isEmpty() == true
    if (startedWithSeparator) {
      cells.removeFirst()
    }
    val endedWithSeparator = cells.lastOrNull()?.isEmpty() == true
    if (endedWithSeparator) {
      cells.removeLast()
    }
    if (cells.isEmpty()) {
      return emptyList()
    }
    val elementRange = textRange
    var left = elementRange.startOffset
    if (startedWithSeparator) {
      left += 1
    }
    var right = left + cells.first().length
    val ranges = mutableListOf<TextRange>()
    ranges.add(TextRange(left, right))
    val cellsSequence = cells.asSequence().drop(1)
    for (cell in cellsSequence) {
      left = right + 1
      right = left + cell.length
      ranges.add(TextRange(left, right))
    }
    return ranges
  }

  val parentTable: MarkdownTable?
    get() = parents(withSelf = true).find { it.hasType(MarkdownElementTypes.TABLE) } as? MarkdownTable

  val cellsRanges: List<TextRange>
    get() = cachedCellsRanges

  val cellsLocalRanges: List<TextRange>
    get() = cachedCellsRanges.map { it.shiftLeft(startOffset) }

  val cellsCount: Int
    get() = cellsRanges.size

  fun getCellRange(index: Int, local: Boolean = false): TextRange? {
    return when {
      local -> cellsLocalRanges.getOrNull(index)
      else -> cellsRanges.getOrNull(index)
    }
  }

  fun getCellRangeWithPipes(index: Int, local: Boolean = false): TextRange? {
    val range = getCellRange(index, local) ?: return null
    val shifted = range.shiftLeft(startOffset)
    val elementText = text
    val left = when {
      elementText.getOrNull(shifted.startOffset - 1) == TableProps.SEPARATOR_CHAR -> range.startOffset - 1
      else -> range.startOffset
    }
    val right = when {
      elementText.getOrNull(shifted.endOffset) == TableProps.SEPARATOR_CHAR -> range.endOffset + 1
      else -> range.endOffset
    }
    return TextRange(left, right)
  }

  fun getCellText(index: Int): String? {
    return getCellRange(index, local = true)?.let { text.substring(it.startOffset, it.endOffset) }
  }

  /**
   * [offset] - offset in document
   */
  fun getColumnIndexFromOffset(offset: Int): Int? {
    return cellsRanges.indexOfFirst { it.containsOffset(offset) }.takeUnless { it == -1 }
  }

  /**
   * [offset] - offset in document
   */
  fun getCellRangeFromOffset(offset: Int): TextRange? {
    return getColumnIndexFromOffset(offset)?.let { cellsRanges[it] }
  }

  enum class CellAlignment {
    NONE,
    LEFT,
    RIGHT,
    CENTER
  }

  fun getCellAlignment(range: TextRange): CellAlignment {
    val cellText = text.subSequence(range.startOffset, range.endOffset)
    val firstIndex = cellText.indexOfFirst { it == ':' }
    if (firstIndex == -1) {
      return CellAlignment.NONE
    }
    val secondIndex = cellText.indexOfLast { it == ':' }
    return when {
      firstIndex != secondIndex -> CellAlignment.CENTER
      cellText.subSequence(0, firstIndex).all { it == ' ' } -> CellAlignment.LEFT
      else -> CellAlignment.RIGHT
    }
  }

  fun getCellAlignment(index: Int): CellAlignment {
    val range = getCellRange(index, local = true)!!
    return getCellAlignment(range)
  }
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.suggested.startOffset
import org.apache.commons.lang.StringUtils
import org.intellij.plugins.markdown.editor.tables.TableUtils.columnsIndices
import org.intellij.plugins.markdown.editor.tables.TableUtils.getColumnAlignment
import org.intellij.plugins.markdown.editor.tables.TableUtils.getColumnCells
import org.intellij.plugins.markdown.editor.tables.TableUtils.isHeaderRow
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableCell
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.jetbrains.annotations.ApiStatus
import java.lang.Integer.max

@ApiStatus.Internal
object TableFormattingUtils {
  private class CellContentState(val contentWithCarets: String, val caretsInside: Array<Caret> = emptyArray()) {
    val trimmedContentWithCarets by lazy { contentWithCarets.trim(' ') }
    val trimmedContentWithoutCarets: String by lazy { StringUtils.remove(trimmedContentWithCarets, TableProps.CARET_REPLACE_CHAR) }
  }

  private fun buildCellState(range: TextRange, document: Document, carets: Iterable<Caret>): CellContentState {
    val caretsInside = carets.filter { range.containsOffset(it.offset) }.sortedBy { it.offset }.toTypedArray()
    val content = document.charsSequence.substring(range.startOffset, range.endOffset)
    if (caretsInside.isEmpty()) {
      return CellContentState(content)
    }
    val caretsOffsets = caretsInside.map { it.offset - range.startOffset }
    check(caretsOffsets.all { it <= content.length }) {
      "Content indices: ${content.indices}; Carets offsets: $caretsOffsets"
    }
    val contentWithReplacements = buildString {
      var previousOffset = 0
      for (caretOffset in caretsOffsets) {
        append(content.substring(previousOffset, caretOffset))
        append(TableProps.CARET_REPLACE_CHAR)
        previousOffset = caretOffset
      }
      append(content.substring(previousOffset, content.length))
    }
    return CellContentState(contentWithReplacements, caretsInside)
  }

  private fun MarkdownTableCell.buildCellState(document: Document, carets: Iterable<Caret>): CellContentState {
    return buildCellState(textRange, document, carets)
  }

  private fun calculateContentsMaxWidth(
    cells: Collection<MarkdownTableCell>,
    cellsContentsWithCarets: Iterable<CellContentState>,
    separatorCellRange: TextRange?,
    trimToMaxContent: Boolean
  ): Int {
    val contentCellsWidth = when {
      trimToMaxContent -> cellsContentsWithCarets.asSequence().map { it.trimmedContentWithoutCarets }.maxOfOrNull { it.length + 2 }
      else -> cells.maxOfOrNull { it.textRange.length }
    }
    checkNotNull(contentCellsWidth)
    return max(max(contentCellsWidth, separatorCellRange?.length ?: -1), TableProps.MIN_CELL_WIDTH)
  }

  private fun calculateNewCaretsPositions(content: String, cellRange: TextRange): Array<Int> {
    val positions = content.indices
      .filter { content[it] == TableProps.CARET_REPLACE_CHAR }
      .map { it + cellRange.startOffset }
      .toTypedArray()
    for (index in positions.indices) {
      positions[index] -= index
    }
    return positions
  }

  private fun processCell(
    document: Document,
    cell: MarkdownTableCell,
    state: CellContentState,
    maxCellWidth: Int,
    alignment: MarkdownTableSeparatorRow.CellAlignment,
    preventExpand: Boolean
  ) {
    val expectedContent = TableModificationUtils.buildRealignedCellContent(
      state.trimmedContentWithCarets,
      maxCellWidth + state.caretsInside.size,
      alignment
    )
    val range = cell.textRange
    val cellContent = document.charsSequence.substring(range.startOffset, range.endOffset)
    if (preventExpand && cellContent.length < maxCellWidth) {
      return
    }
    val expectedContentWithoutCarets = expectedContent.replace(TableProps.CARET_REPLACE_CHAR.toString(), "")
    if (cellContent != expectedContentWithoutCarets) {
      document.replaceString(range.startOffset, range.endOffset, expectedContentWithoutCarets)
      val caretsPositions = calculateNewCaretsPositions(expectedContent, range)
      check(caretsPositions.size == state.caretsInside.size)
      for ((caret, position) in state.caretsInside.asSequence().zip(caretsPositions.asSequence())) {
        caret.moveToOffset(position)
      }
    }
  }

  private fun processSeparator(
    document: Document,
    separatorRow: MarkdownTableSeparatorRow,
    columnIndex: Int,
    maxCellWidth: Int,
    preventExpand: Boolean
  ) {
    val range = separatorRow.getCellRange(columnIndex)!!
    val content = document.charsSequence.substring(range.startOffset, range.endOffset)
    if (preventExpand && content.length < maxCellWidth) {
      return
    }
    val alignment = separatorRow.getCellAlignment(columnIndex)
    val expectedContent = TableModificationUtils.buildSeparatorCellContent(alignment, maxCellWidth)
    if (content != expectedContent) {
      document.replaceString(range.startOffset, range.endOffset, expectedContent)
    }
  }

  fun MarkdownTable.reformatColumnOnChange(
    document: Document,
    carets: Iterable<Caret>,
    columnIndex: Int,
    trimToMaxContent: Boolean,
    preventExpand: Boolean = false
  ) {
    val cells = getColumnCells(columnIndex, withHeader = true).asReversed()
    val cellsStates = cells.map { it.buildCellState(document, carets) }
    val separatorRow = checkNotNull(separatorRow)
    val separatorCellRange = separatorRow.getCellRange(columnIndex)!!
    val maxCellWidth = calculateContentsMaxWidth(
      cells,
      cellsStates,
      separatorCellRange.takeIf { carets.any { separatorCellRange.containsOffset(it.offset) } },
      trimToMaxContent
    )
    val alignment = getColumnAlignment(columnIndex)
    val contentCells = cells.asSequence().zip(cellsStates.asSequence()).takeWhile { (cell, _) -> cell.parentRow?.isHeaderRow == false }
    for ((cell, state) in contentCells) {
      processCell(document, cell, state, maxCellWidth, alignment, preventExpand)
    }
    processSeparator(document, separatorRow, columnIndex, maxCellWidth, preventExpand)
    processCell(document, cells.last(), cellsStates.last(), maxCellWidth, alignment, preventExpand)
  }

  fun reformatAllColumns(table: MarkdownTable, document: Document, trimToMaxContent: Boolean = true, preventExpand: Boolean = false) {
    val columnsIndices = table.columnsIndices
    val tableOffset = table.startOffset
    for (columnIndex in columnsIndices) {
      PsiDocumentManager.getInstance(table.project).commitDocument(document)
      val currentTable = TableUtils.findTable(table.containingFile, tableOffset) ?: break
      if (columnIndex !in currentTable.columnsIndices) {
        break
      }
      currentTable.reformatColumnOnChange(document, emptyList(), columnIndex, trimToMaxContent, preventExpand)
    }
  }

  fun MarkdownTable.isSoftWrapping(editor: Editor): Boolean {
    val range = textRange
    return editor.softWrapModel.getSoftWrapsForRange(range.startOffset, range.endOffset).isNotEmpty()
  }
}

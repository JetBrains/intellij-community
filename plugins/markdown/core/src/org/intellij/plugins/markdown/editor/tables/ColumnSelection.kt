package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.siblings
import com.intellij.psi.util.startOffset
import org.intellij.plugins.markdown.editor.tables.TableUtils.columnsIndices
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableCell
import org.intellij.plugins.markdown.lang.psi.util.hasType

internal fun MarkdownTable.selectColumn(
  editor: Editor,
  columnIndex: Int,
  withHeader: Boolean = false,
  withSeparator: Boolean = false,
  withBorders: Boolean = false,
  selectionMode: TableSelectionMode = TableSelectionMode.REPLACE
) {
  if (columnIndex !in columnsIndices) return
  editor.selectTableParts(this, TableSelectionAxis.COLUMN, columnIndex, selectionMode) { indices ->
    val groups = indices.toContiguousRanges()
    val cellRanges = getRows(withHeader).flatMap { row ->
      groups.mapNotNull { group ->
        val selectedCells = row.cells.filter { it.columnIndex in group }
        val firstCell = selectedCells.firstOrNull() ?: return@mapNotNull null
        val lastCell = selectedCells.last()
        val firstRange = obtainCellSelectionRange(firstCell, withBorders)
        val lastRange = obtainCellSelectionRange(lastCell, withBorders)
        TextRange(firstRange.startOffset, lastRange.endOffset)
      }
    }
    val separatorRanges = if (withSeparator) {
      groups.mapNotNull { group ->
        val ranges = group.mapNotNull { columnIndex ->
          when {
            withBorders -> separatorRow?.getCellRangeWithPipes(columnIndex)
            else -> separatorRow?.getCellRange(columnIndex)
          }
        }
        val firstRange = ranges.firstOrNull()
        val lastRange = ranges.lastOrNull()
        if (firstRange == null || lastRange == null) null else TextRange(firstRange.startOffset, lastRange.endOffset)
      }
    }
    else {
      emptyList()
    }
    cellRanges + separatorRanges
  }
}

private fun obtainCellSelectionRange(cell: MarkdownTableCell, withBorders: Boolean): TextRange {
  val range = cell.textRange
  if (!withBorders) {
    return range
  }
  val leftPipe = cell.siblings(forward = false, withSelf = false)
    .takeWhile { it !is MarkdownTableCell }
    .find { it.hasType(MarkdownTokenTypes.TABLE_SEPARATOR) }
  val rightPipe = cell.siblings(forward = true, withSelf = false)
    .takeWhile { it !is MarkdownTableCell }
    .find { it.hasType(MarkdownTokenTypes.TABLE_SEPARATOR) }
  val left = leftPipe?.startOffset ?: range.startOffset
  val right = rightPipe?.endOffset ?: range.endOffset
  return TextRange(left, right)
}

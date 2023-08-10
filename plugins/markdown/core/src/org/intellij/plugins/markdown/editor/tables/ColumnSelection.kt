package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.siblings
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.editor.tables.TableUtils.getColumnCells
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
  withBorders: Boolean = false
) {
  val cells = getColumnCells(columnIndex, withHeader)
  val caretModel = editor.caretModel
  caretModel.removeSecondaryCarets()
  caretModel.currentCaret.apply {
    val textRange = obtainCellSelectionRange(cells.first(), withBorders)
    moveToOffset(textRange.startOffset)
    setSelectionFromRange(textRange)
  }
  if (withSeparator) {
    val range = when {
      withBorders -> separatorRow?.getCellRangeWithPipes(columnIndex)
      else -> separatorRow?.getCellRange(columnIndex)
    }
    range?.let { textRange ->
      val caret = caretModel.addCaret(editor.offsetToVisualPosition(textRange.startOffset))
      caret?.setSelectionFromRange(textRange)
    }
  }
  for (cell in cells.asSequence().drop(1)) {
    val textRange = obtainCellSelectionRange(cell, withBorders)
    val caret = caretModel.addCaret(editor.offsetToVisualPosition(textRange.startOffset))
    caret?.setSelectionFromRange(textRange)
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

private fun Caret.setSelectionFromRange(textRange: TextRange) {
  setSelection(textRange.startOffset, textRange.endOffset)
}

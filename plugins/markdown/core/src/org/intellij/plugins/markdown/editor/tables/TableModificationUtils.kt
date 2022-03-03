// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.siblings
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.containers.ContainerUtil
import org.intellij.plugins.markdown.editor.tables.TableUtils.columnsCount
import org.intellij.plugins.markdown.editor.tables.TableUtils.getColumnAlignment
import org.intellij.plugins.markdown.editor.tables.TableUtils.getColumnCells
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableCell
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.intellij.plugins.markdown.util.hasType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
object TableModificationUtils {
  /**
   * Modifies column with [columnIndex], calling [transformCell] on each cell and
   * [transformSeparator] on corresponding separator cell.
   */
  fun MarkdownTable.modifyColumn(
    columnIndex: Int,
    transformSeparator: (TextRange) -> Unit,
    transformCell: (MarkdownTableCell) -> Unit
  ): Boolean {
    val separatorRange = separatorRow?.getCellRange(columnIndex) ?: return false
    val headerCell = headerRow?.getCell(columnIndex) ?: return false
    val cells = getColumnCells(columnIndex, withHeader = false)
    for (cell in cells.asReversed()) {
      transformCell.invoke(cell)
    }
    transformSeparator.invoke(separatorRange)
    transformCell.invoke(headerCell)
    return true
  }

  private fun getCellPotentialWidth(cellText: String): Int {
    var width = cellText.length
    if (!cellText.startsWith(' ')) {
      width += 1
    }
    if (!cellText.endsWith(' ')) {
      width += 1
    }
    return width
  }

  private fun isSeparatorCellCorrectlyFormatted(cellText: String): Boolean {
    // Don't have to validate ':' count and positions, since it won't be a separator at all
    return cellText.all { it =='-' || it == ':' }
  }

  fun MarkdownTableCell.hasCorrectPadding(): Boolean {
    val cellText = text
    return text.length >= TableProps.MIN_CELL_WIDTH && cellText.startsWith(" ") && cellText.endsWith(" ")
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun MarkdownTable.isColumnCorrectlyFormatted(columnIndex: Int, checkAlignment: Boolean = true): Boolean {
    val cells = getColumnCells(columnIndex, withHeader = true)
    if (cells.isEmpty()) {
      return true
    }
    if (checkAlignment && !validateColumnAlignment(columnIndex)) {
      return false
    }
    val separatorCellText = separatorRow?.getCellText(columnIndex)!!
    val width = getCellPotentialWidth(cells.first().text)
    if (separatorCellText.length != width || !isSeparatorCellCorrectlyFormatted(separatorCellText)) {
      return false
    }
    return cells.all {
      val selfWidth = getCellPotentialWidth(it.text)
      it.hasCorrectPadding() && selfWidth == it.textRange.length && selfWidth == width
    }
  }

  fun MarkdownTable.isCorrectlyFormatted(checkAlignment: Boolean = true): Boolean {
    return (0 until columnsCount).all { isColumnCorrectlyFormatted(it, checkAlignment) }
  }

  fun MarkdownTableCell.hasValidAlignment(): Boolean {
    val table = parentTable ?: return true
    val columnAlignment = table.getColumnAlignment(columnIndex)
    return hasValidAlignment(columnAlignment)
  }

  fun MarkdownTableCell.hasValidAlignment(expected: MarkdownTableSeparatorRow.CellAlignment): Boolean {
    val content = text
    if (content.length < TableProps.MIN_CELL_WIDTH) {
      return false
    }
    if (content.isBlank()) {
      return true
    }
    when (expected) {
      MarkdownTableSeparatorRow.CellAlignment.LEFT -> {
        return content[0] == ' ' && content[1] != ' '
      }
      MarkdownTableSeparatorRow.CellAlignment.RIGHT -> {
        return content.last() == ' ' && content[content.lastIndex - 1] != ' '
      }
      MarkdownTableSeparatorRow.CellAlignment.CENTER -> {
        var spacesLeft = content.indexOfFirst { it != ' ' }
        var spacesRight = content.indexOfLast { it != ' ' }
        if (spacesLeft == -1 || spacesRight == -1) {
          return true
        }
        spacesLeft += 1
        spacesRight = content.lastIndex - spacesRight + 1
        return spacesLeft == spacesRight || (spacesLeft + 1 == spacesRight)
      }
      else -> return true
    }
  }

  fun MarkdownTable.validateColumnAlignment(columnIndex: Int): Boolean {
    val expected = separatorRow!!.getCellAlignment(columnIndex)
    if (expected == MarkdownTableSeparatorRow.CellAlignment.NONE) {
      return true
    }
    return getColumnCells(columnIndex, true).all { it.hasValidAlignment(expected) }
  }

  /**
   * @param cellContentWidth Should be at least 5
   */
  fun buildSeparatorCellContent(alignment: MarkdownTableSeparatorRow.CellAlignment, cellContentWidth: Int): String {
    check(cellContentWidth > 4)
    return when (alignment) {
      MarkdownTableSeparatorRow.CellAlignment.NONE -> "-".repeat(cellContentWidth)
      MarkdownTableSeparatorRow.CellAlignment.LEFT -> ":${"-".repeat(cellContentWidth - 1)}"
      MarkdownTableSeparatorRow.CellAlignment.RIGHT -> "${"-".repeat(cellContentWidth - 1)}:"
      MarkdownTableSeparatorRow.CellAlignment.CENTER -> ":${"-".repeat(cellContentWidth - 2)}:"
    }
  }

  fun buildRealignedCellContent(cellContent: String, wholeCellWidth: Int, alignment: MarkdownTableSeparatorRow.CellAlignment): String {
    check(wholeCellWidth >= cellContent.length)
    return when (alignment) {
      MarkdownTableSeparatorRow.CellAlignment.RIGHT -> "${" ".repeat(wholeCellWidth - cellContent.length - 1)}$cellContent "
      MarkdownTableSeparatorRow.CellAlignment.CENTER -> {
        val leftPadding = (wholeCellWidth - cellContent.length) / 2
        val rightPadding = wholeCellWidth - cellContent.length - leftPadding
        buildString {
          repeat(leftPadding) {
            append(' ')
          }
          append(cellContent)
          repeat(rightPadding) {
            append(' ')
          }
        }
      }
      // MarkdownTableSeparatorRow.CellAlignment.LEFT
      else -> " $cellContent${" ".repeat(wholeCellWidth - cellContent.length - 1)}"
    }
  }

  fun MarkdownTableCell.getContentWithoutWhitespaces(document: Document): String {
    val range = textRange
    val content = document.charsSequence.substring(range.startOffset, range.endOffset)
    return content.trim(' ')
  }

  fun MarkdownTableSeparatorRow.updateAlignment(document: Document, columnIndex: Int, alignment: MarkdownTableSeparatorRow.CellAlignment) {
    val cellRange = getCellRange(columnIndex)!!
    val width = cellRange.length
    //check(width >= TableProps.MIN_CELL_WIDTH)
    val replacement = buildSeparatorCellContent(alignment, width)
    document.replaceString(cellRange.startOffset, cellRange.endOffset, replacement)
  }

  fun MarkdownTableCell.updateAlignment(document: Document, alignment: MarkdownTableSeparatorRow.CellAlignment) {
    if (alignment == MarkdownTableSeparatorRow.CellAlignment.NONE) {
      return
    }
    val documentText = document.charsSequence
    val cellRange = textRange
    val cellText = documentText.substring(cellRange.startOffset, cellRange.endOffset)
    val actualContent = cellText.trim(' ')
    val replacement = buildRealignedCellContent(actualContent, cellText.length, alignment)
    document.replaceString(cellRange.startOffset, cellRange.endOffset, replacement)
  }

  fun MarkdownTable.updateColumnAlignment(document: Document, columnIndex: Int, alignment: MarkdownTableSeparatorRow.CellAlignment) {
    modifyColumn(
      columnIndex,
      transformSeparator = { separatorRow?.updateAlignment(document, columnIndex, alignment) },
      transformCell = { it.updateAlignment(document, alignment) }
    )
  }

  fun MarkdownTable.updateColumnAlignment(document: Document, columnIndex: Int) {
    val alignment = separatorRow?.getCellAlignment(columnIndex) ?: return
    updateColumnAlignment(document, columnIndex, alignment)
  }

  fun MarkdownTable.buildEmptyRow(builder: StringBuilder = StringBuilder()): StringBuilder {
    val header = checkNotNull(headerRow)
    builder.append(TableProps.SEPARATOR_CHAR)
    for (cell in header.cells) {
      repeat(cell.textRange.length) {
        builder.append(' ')
      }
      builder.append(TableProps.SEPARATOR_CHAR)
    }
    return builder
  }

  fun MarkdownTable.selectColumn(
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

  fun MarkdownTable.insertColumn(
    document: Document,
    columnIndex: Int,
    after: Boolean = true,
    alignment: MarkdownTableSeparatorRow.CellAlignment = MarkdownTableSeparatorRow.CellAlignment.NONE,
    columnWidth: Int = TableProps.MIN_CELL_WIDTH
  ) {
    val cells = getColumnCells(columnIndex, withHeader = false)
    val headerCell = headerRow?.getCell(columnIndex)!!
    val separatorCell = separatorRow?.getCellRange(columnIndex)!!
    val cellContent = " ".repeat(columnWidth)
    for (cell in cells.asReversed()) {
      when {
        after -> document.insertString(cell.endOffset + 1, "${cellContent}${TableProps.SEPARATOR_CHAR}")
        else -> document.insertString(cell.startOffset - 1, "${TableProps.SEPARATOR_CHAR}${cellContent}")
      }
    }
    when {
      after -> document.insertString(separatorCell.endOffset + 1, "${buildSeparatorCellContent(alignment, columnWidth)}${TableProps.SEPARATOR_CHAR}")
      else -> document.insertString(separatorCell.startOffset - 1, "${TableProps.SEPARATOR_CHAR}${buildSeparatorCellContent(alignment, columnWidth)}")
    }
    when {
      after -> document.insertString(headerCell.endOffset + 1, "${cellContent}${TableProps.SEPARATOR_CHAR}")
      else -> document.insertString(headerCell.startOffset - 1, "${TableProps.SEPARATOR_CHAR}${cellContent}")
    }
  }

  fun buildEmptyRow(columns: Int, fillCharacter: Char = ' ', builder: StringBuilder = StringBuilder()): StringBuilder {
    return builder.apply {
      repeat(columns) {
        append(TableProps.SEPARATOR_CHAR)
        repeat(TableProps.MIN_CELL_WIDTH) {
          append(fillCharacter)
        }
      }
      append(TableProps.SEPARATOR_CHAR)
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun buildHeaderSeparator(columns: Int, builder: StringBuilder = StringBuilder()): StringBuilder {
    return buildEmptyRow(columns, '-', builder)
  }

  fun buildEmptyTable(contentRows: Int, columns: Int): String {
    val builder = StringBuilder()
    buildEmptyRow(columns, builder = builder)
    builder.append('\n')
    buildHeaderSeparator(columns, builder = builder)
    builder.append('\n')
    repeat(contentRows) {
      buildEmptyRow(columns, builder = builder)
      builder.append('\n')
    }
    return builder.toString()
  }

  fun MarkdownTableRow.hasCorrectBorders(): Boolean {
    return firstChild?.hasType(MarkdownTokenTypes.TABLE_SEPARATOR) == true &&
           lastChild?.hasType(MarkdownTokenTypes.TABLE_SEPARATOR) == true
  }

  fun MarkdownTableSeparatorRow.hasCorrectBorders(): Boolean {
    return text.let { it.startsWith(TableProps.SEPARATOR_CHAR) && it.endsWith(TableProps.SEPARATOR_CHAR) }
  }

  fun MarkdownTable.hasCorrectBorders(): Boolean {
    val rows = getRows(true)
    return rows.all { it.hasCorrectBorders() } && separatorRow?.hasCorrectBorders() == true
  }

  /**
   * Removes cell based on PSI.
   */
  fun MarkdownTableSeparatorRow.removeCell(columnIndex: Int) {
    val contents = (0 until cellsCount).map { it to getCellText(it) }
    val newContents = contents.asSequence()
      .filter { (index, _) -> index != columnIndex }
      .map { (_, text) -> text }
      .joinToString(
        TableProps.SEPARATOR_CHAR.toString(),
        prefix = TableProps.SEPARATOR_CHAR.toString(),
        postfix = TableProps.SEPARATOR_CHAR.toString()
      )
    replaceWithText(newContents)
  }

  /**
   * Swaps two cells based on PSI.
   */
  fun MarkdownTableSeparatorRow.swapCells(leftIndex: Int, rightIndex: Int) {
    val contents = (0 until cellsCount).asSequence().map { getCellText(it) }.toMutableList()
    ContainerUtil.swapElements(contents, leftIndex, rightIndex)
    val newContents = contents.joinToString(
      TableProps.SEPARATOR_CHAR.toString(),
      prefix = TableProps.SEPARATOR_CHAR.toString(),
      postfix = TableProps.SEPARATOR_CHAR.toString()
    )
    replaceWithText(newContents)
  }

  /**
   * Removes column based on PSI.
   */
  fun MarkdownTable.removeColumn(columnIndex: Int) {
    val cells = getColumnCells(columnIndex, withHeader = true)
    for (cell in cells.asReversed()) {
      val parent = cell.parent
      when {
        columnIndex == 0 && cell.prevSibling?.hasType(MarkdownTokenTypes.TABLE_SEPARATOR) == true -> parent.deleteChildRange(cell.prevSibling, cell)
        cell.nextSibling?.hasType(MarkdownTokenTypes.TABLE_SEPARATOR) == true -> parent.deleteChildRange(cell, cell.nextSibling)
        else -> cell.delete()
      }
    }
    separatorRow?.removeCell(columnIndex)
  }
}

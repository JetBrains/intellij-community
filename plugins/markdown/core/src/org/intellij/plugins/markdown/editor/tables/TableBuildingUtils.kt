package org.intellij.plugins.markdown.editor.tables

import org.intellij.plugins.markdown.editor.tables.TableUtils.getTableStyle
import org.intellij.plugins.markdown.lang.formatter.settings.TableStyle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.jetbrains.annotations.ApiStatus

internal fun buildEmptyRow(
  columns: Int,
  fillCharacter: Char = ' ',
  width: Int = 5,
  builder: StringBuilder = StringBuilder()
): StringBuilder {
  return builder.apply {
    repeat(columns) {
      append(TableProps.SEPARATOR_CHAR)
      repeat(width) {
        append(fillCharacter)
      }
    }
    append(TableProps.SEPARATOR_CHAR)
  }
}

internal fun buildHeaderSeparator(columns: Int, width: Int = 5, builder: StringBuilder = StringBuilder()): StringBuilder {
  return buildEmptyRow(columns, '-', width, builder)
}

@ApiStatus.Internal
fun buildEmptyTable(contentRows: Int, columns: Int, cellWidth: Int = 5, tableStyle: TableStyle): String {
  val builder = StringBuilder()
  val contentWidth = when (tableStyle) {
    TableStyle.ALIGNED -> cellWidth
    TableStyle.COMPACT -> 1
    TableStyle.TIGHT -> 0
  }
  buildEmptyRow(columns, width = contentWidth, builder = builder)
  builder.append('\n')
  if (tableStyle == TableStyle.ALIGNED) {
    buildHeaderSeparator(columns, width = cellWidth, builder = builder)
  }
  else {
    val separatorCell = if (tableStyle == TableStyle.COMPACT) " --- " else "---"
    repeat(columns) {
      builder.append(TableProps.SEPARATOR_CHAR).append(separatorCell)
    }
    builder.append(TableProps.SEPARATOR_CHAR)
  }
  builder.append('\n')
  repeat(contentRows) {
    buildEmptyRow(columns, width = contentWidth, builder = builder)
    builder.append('\n')
  }
  return builder.toString()
}

/**
 * Builds new table row text based on the width of cells inside header row.
 */
internal fun MarkdownTable.buildEmptyRow(builder: StringBuilder = StringBuilder(), tableStyle: TableStyle = getTableStyle(this.containingFile)): StringBuilder {
  val header = checkNotNull(headerRow)
  if (tableStyle != TableStyle.ALIGNED) {
    return buildEmptyRow(header.cells.size, width = if (tableStyle == TableStyle.COMPACT) 1 else 0, builder = builder)
  }
  builder.append(TableProps.SEPARATOR_CHAR)
  for (cell in header.cells) {
    repeat(TableCharacterWidthUtils.calculateDisplayWidth(cell.text)) {
      builder.append(' ')
    }
    builder.append(TableProps.SEPARATOR_CHAR)
  }
  return builder
}

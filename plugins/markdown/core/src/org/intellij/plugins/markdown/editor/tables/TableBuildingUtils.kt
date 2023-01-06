package org.intellij.plugins.markdown.editor.tables

import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable

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

internal fun buildEmptyTable(contentRows: Int, columns: Int, cellWidth: Int = 5): String {
  val builder = StringBuilder()
  buildEmptyRow(columns, width = cellWidth, builder = builder)
  builder.append('\n')
  buildHeaderSeparator(columns, width = cellWidth, builder = builder)
  builder.append('\n')
  repeat(contentRows) {
    buildEmptyRow(columns, width = cellWidth, builder = builder)
    builder.append('\n')
  }
  return builder.toString()
}

/**
 * Builds new table row text based on the width of cells inside header row.
 */
internal fun MarkdownTable.buildEmptyRow(builder: StringBuilder = StringBuilder()): StringBuilder {
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

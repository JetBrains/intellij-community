package org.intellij.plugins.markdown.editor.tables

import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import kotlin.math.max

internal fun buildEmptyRow(columns: Int, fillCharacter: Char = ' ', builder: StringBuilder = StringBuilder()): StringBuilder {
  val width = max(TableProps.MIN_CELL_WIDTH, 3)
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

internal fun buildHeaderSeparator(columns: Int, builder: StringBuilder = StringBuilder()): StringBuilder {
  return buildEmptyRow(columns, '-', builder)
}

internal fun buildEmptyTable(contentRows: Int, columns: Int): String {
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

package com.jetbrains.packagesearch.intellij.plugin.ui.util

import javax.swing.JTable
import javax.swing.table.TableColumn

internal fun JTable.autosizeColumnsAt(indices: Iterable<Int>) {
    val columns = indices.map { columnModel.getColumn(it) }
    val preferredWidths = getColumnDataWidths(columns)

    columns.forEachIndexed { index, column ->
        column.width = preferredWidths[index]
    }
}

private fun JTable.getColumnDataWidths(columns: Iterable<TableColumn>): IntArray {
    val preferredWidth = IntArray(columns.count())
    val separatorSize = intercellSpacing.width

    for ((index, column) in columns.withIndex()) {
        val maxWidth = column.maxWidth
        val columnIndex = column.modelIndex

        for (row in 0 until rowCount) {
            val width = getCellDataWidth(row, columnIndex) + separatorSize
            preferredWidth[index] = preferredWidth[index].coerceAtLeast(width)
            if (preferredWidth[index] >= maxWidth) break
        }
    }
    return preferredWidth
}

private fun JTable.getCellDataWidth(row: Int, column: Int): Int {
    val renderer = getCellRenderer(row, column)
    val cellWidth = prepareRenderer(renderer, row, column).preferredSize.width
    return cellWidth + intercellSpacing.width
}

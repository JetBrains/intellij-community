/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JTable
import javax.swing.table.TableColumn

internal suspend fun JTable.autosizeColumnsAt(indices: Iterable<Int>) {
    val (columns, preferredWidths) = withContext(Dispatchers.Default) {
        val columns = indices.map { columnModel.getColumn(it) }
        columns to getColumnDataWidths(columns)
    }
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

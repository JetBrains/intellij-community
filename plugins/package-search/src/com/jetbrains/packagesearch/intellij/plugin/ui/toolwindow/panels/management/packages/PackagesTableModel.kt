package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel

internal class PackagesTableModel(
    vararg val columns: ColumnInfo<PackagesTableItem<*>, *>
) : ListTableModel<PackagesTableItem<*>>(*columns) {

    override fun getRowCount() = items.size
    override fun getColumnCount() = columns.size
    override fun getColumnClass(columnIndex: Int): Class<out Any> = columns[columnIndex].javaClass
}

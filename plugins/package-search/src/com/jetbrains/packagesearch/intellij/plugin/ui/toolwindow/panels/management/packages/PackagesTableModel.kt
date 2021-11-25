package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ActionsColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.NameColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ScopeColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.VersionColumn

internal class PackagesTableModel(
    val nameColumn: NameColumn,
    val scopeColumn: ScopeColumn,
    val versionColumn: VersionColumn,
    val actionsColumn: ActionsColumn
) : ListTableModel<PackagesTableItem<*>>(nameColumn, scopeColumn, versionColumn, actionsColumn) {

    val columns by lazy { arrayOf(nameColumn, scopeColumn, versionColumn, actionsColumn) }

    override fun getRowCount() = items.size
    override fun getColumnCount() = columns.size
    override fun getColumnClass(columnIndex: Int): Class<out Any> = columns[columnIndex].javaClass

    fun columnIndexOf(info: ColumnInfo<PackagesTableItem<*>, *>) = when (info) {
        nameColumn -> 0
        scopeColumn -> 1
        versionColumn -> 2
        actionsColumn -> 3
        else -> error("Column not known")
    }
}

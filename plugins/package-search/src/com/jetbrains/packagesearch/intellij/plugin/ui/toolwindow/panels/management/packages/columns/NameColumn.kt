package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import com.intellij.util.ui.ColumnInfo
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PackageNameCellRenderer
import javax.swing.table.TableCellRenderer

internal class NameColumn : ColumnInfo<PackagesTableItem<*>, PackagesTableItem<*>>(
    PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.name")
) {

    override fun getRenderer(item: PackagesTableItem<*>): TableCellRenderer = PackageNameCellRenderer

    override fun valueOf(item: PackagesTableItem<*>) = item
}

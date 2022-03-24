package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import com.intellij.util.ui.ColumnInfo
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.editors.PackageScopeTableCellEditor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PackageScopeTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

internal class ScopeColumn(
    private val scopeSetter: (uiPackageModel: UiPackageModel<*>, newScope: PackageScope) -> Unit
) : ColumnInfo<PackagesTableItem<*>, PackagesTableItem<*>>(
    PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.scope")
) {

    override fun getRenderer(item: PackagesTableItem<*>): TableCellRenderer = PackageScopeTableCellRenderer

    override fun getEditor(item: PackagesTableItem<*>): TableCellEditor = PackageScopeTableCellEditor

    override fun isCellEditable(item: PackagesTableItem<*>?) = true

    override fun valueOf(item: PackagesTableItem<*>) = item

    override fun setValue(item: PackagesTableItem<*>?, value: PackagesTableItem<*>?) {
        if (value == null) return
        if (value.uiPackageModel.selectedScope == item?.uiPackageModel?.selectedScope) return

        scopeSetter(value.uiPackageModel, value.uiPackageModel.selectedScope)
    }
}

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import com.intellij.util.ui.ColumnInfo
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.editors.PackageScopeTableCellEditor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PackageScopeTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

internal class ScopeColumn(
    private val scopeSetter: (packageModel: PackageModel, newScope: PackageScope) -> Unit
) : ColumnInfo<PackagesTableItem<*>, ScopeViewModel<*>>(
    PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.scope")
) {

    override fun getRenderer(item: PackagesTableItem<*>): TableCellRenderer = PackageScopeTableCellRenderer

    override fun getEditor(item: PackagesTableItem<*>): TableCellEditor = PackageScopeTableCellEditor

    override fun isCellEditable(item: PackagesTableItem<*>?) = true

    override fun valueOf(item: PackagesTableItem<*>): ScopeViewModel<*> = when (item) {
        is PackagesTableItem.InstalledPackage -> {
            val selectedScope = item.installedScopes.first()
            ScopeViewModel.InstalledPackage(item.packageModel, item.installedScopes, item.defaultScope, selectedScope = selectedScope)
        }
        is PackagesTableItem.InstallablePackage -> {
            val selectedScope = item.selectedPackageModel.selectedScope
            ScopeViewModel.InstallablePackage(item.packageModel, item.availableScopes, item.defaultScope, selectedScope = selectedScope)
        }
    }

    override fun setValue(item: PackagesTableItem<*>?, value: ScopeViewModel<*>?) {
        if (value !is ScopeViewModel) return

        scopeSetter(value.packageModel, value.selectedScope)
    }
}

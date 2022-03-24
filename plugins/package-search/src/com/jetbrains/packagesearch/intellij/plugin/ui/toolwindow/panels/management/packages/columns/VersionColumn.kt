package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import com.intellij.util.ui.ColumnInfo
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.editors.PackageVersionTableCellEditor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PackageVersionTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

internal class VersionColumn(
    private val versionSetter: (uiPackageModel: UiPackageModel<*>, newVersion: NormalizedPackageVersion<*>) -> Unit
) : ColumnInfo<PackagesTableItem<*>, UiPackageModel<*>>(
    PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.versions")
) {

    private val cellRenderer = PackageVersionTableCellRenderer()
    private val cellEditor = PackageVersionTableCellEditor()

    private var onlyStable: Boolean = false
    private var targetModules: TargetModules = TargetModules.None

    override fun getRenderer(item: PackagesTableItem<*>): TableCellRenderer = cellRenderer

    override fun getEditor(item: PackagesTableItem<*>): TableCellEditor = cellEditor

    override fun isCellEditable(item: PackagesTableItem<*>?) =
        item?.uiPackageModel?.sortedVersions?.isNotEmpty() ?: false

    fun updateData(onlyStable: Boolean, targetModules: TargetModules) {
        this.onlyStable = onlyStable
        this.targetModules = targetModules
        cellRenderer.updateData(onlyStable)
        cellEditor.updateData(onlyStable)
    }

    override fun valueOf(item: PackagesTableItem<*>): UiPackageModel<*> =
        when (item) {
            is PackagesTableItem.InstalledPackage -> item.uiPackageModel
            is PackagesTableItem.InstallablePackage -> item.uiPackageModel
        }

    override fun setValue(item: PackagesTableItem<*>?, value: UiPackageModel<*>?) {
        val selectedVersion = value?.selectedVersion
        if (selectedVersion == null) return
        if (selectedVersion == item?.uiPackageModel?.selectedVersion) return

        versionSetter(value, selectedVersion)
    }
}

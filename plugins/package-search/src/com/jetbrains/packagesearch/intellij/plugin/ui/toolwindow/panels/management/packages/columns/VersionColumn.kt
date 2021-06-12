package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import com.intellij.util.ui.ColumnInfo
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.editors.PackageVersionTableCellEditor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PackageVersionTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

internal class VersionColumn(
    private val versionSetter: (packageModel: PackageModel, newVersion: PackageVersion) -> Unit
) : ColumnInfo<PackagesTableItem<*>, VersionViewModel<*>>(
    PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.versions")
) {

    private val cellRenderer = PackageVersionTableCellRenderer()
    private val cellEditor = PackageVersionTableCellEditor()

    private var onlyStable: Boolean = false
    private var targetModules: TargetModules = TargetModules.None

    override fun getRenderer(item: PackagesTableItem<*>): TableCellRenderer = cellRenderer

    override fun getEditor(item: PackagesTableItem<*>): TableCellEditor = cellEditor

    override fun isCellEditable(item: PackagesTableItem<*>?) =
        item?.packageModel?.getAvailableVersions(onlyStable)?.isNotEmpty() ?: false

    fun updateData(onlyStable: Boolean, targetModules: TargetModules) {
        this.onlyStable = onlyStable
        this.targetModules = targetModules
        cellRenderer.updateData(onlyStable)
        cellEditor.updateData(onlyStable)
    }

    override fun valueOf(item: PackagesTableItem<*>) = when (item) {
        is PackagesTableItem.InstalledPackage -> {
            val latestInstalledVersion = when (val modules = targetModules) {
                TargetModules.None -> throw IllegalStateException(
                    "Trying to get version value for ${item.packageModel.identifier} when there is no target module"
                )
                is TargetModules.All -> item.packageModel.getLatestInstalledVersion()
                else ->
                    item.packageModel.usageInfo
                        .filter { usageInfo -> modules.any { usageInfo.projectModule == it.projectModule } }
                        .map { it.version }
                        .maxOrNull()
                        ?: throw IllegalStateException(
                            "Unable to find usage for supposedly installed package ${item.packageModel.identifier} " +
                                "(modules: ${modules.modules.joinToString { it.projectModule.name }})"
                        )
            }

            VersionViewModel.InstalledPackage(packageModel = item.packageModel, selectedVersion = latestInstalledVersion)
        }
        is PackagesTableItem.InstallablePackage -> {
            VersionViewModel.InstallablePackage(item.packageModel, item.selectedPackageModel.selectedVersion)
        }
    }

    override fun setValue(item: PackagesTableItem<*>?, value: VersionViewModel<*>?) {
        if (value !is VersionViewModel) return

        versionSetter(value.packageModel, value.selectedVersion)
    }
}

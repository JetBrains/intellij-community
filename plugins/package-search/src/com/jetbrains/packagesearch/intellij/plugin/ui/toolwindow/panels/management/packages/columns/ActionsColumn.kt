package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import com.intellij.openapi.project.Project
import com.intellij.util.ui.ColumnInfo
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageOperationType
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PackageActionsTableCellRendererAndEditor
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.Nls
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

internal class ActionsColumn(
    private val project: Project,
    private val operationExecutor: (Deferred<List<PackageSearchOperation<*>>>) -> Unit
) : ColumnInfo<PackagesTableItem<*>, Any>(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.actions")) {

    private var targetModules: TargetModules = TargetModules.None
    private var knownRepositoriesInTargetModules = KnownRepositories.InTargetModules.EMPTY
    private var onlyStable = false

    private val cellRendererAndEditor = PackageActionsTableCellRendererAndEditor {
        operationExecutor(it.operations)
    }

    override fun getRenderer(item: PackagesTableItem<*>): TableCellRenderer = cellRendererAndEditor

    override fun getEditor(item: PackagesTableItem<*>): TableCellEditor = cellRendererAndEditor

    override fun isCellEditable(item: PackagesTableItem<*>) = getOperationTypeFor(item) != null

    fun updateData(
        onlyStable: Boolean,
        targetModules: TargetModules,
        knownRepositoriesInTargetModules: KnownRepositories.InTargetModules
    ) {
        this.onlyStable = onlyStable
        this.targetModules = targetModules
        this.knownRepositoriesInTargetModules = knownRepositoriesInTargetModules
    }

    override fun valueOf(item: PackagesTableItem<*>): ActionViewModel {
        val operationType = getOperationTypeFor(item)
        return ActionViewModel(
            item.packageModel,
            item.uiPackageModel.packageOperations.primaryOperations,
            operationType,
            generateMessageFor(item),
            isSearchResult = item is PackagesTableItem.InstallablePackage
        )
    }

    private fun getOperationTypeFor(item: PackagesTableItem<*>): PackageOperationType? =
        when (item) {
            is PackagesTableItem.InstalledPackage -> {
                val currentVersion = item.uiPackageModel.selectedVersion

                val packageOperations = item.uiPackageModel.packageOperations
                when {
                    currentVersion is NormalizedPackageVersion.Missing -> PackageOperationType.SET
                    packageOperations.canUpgradePackage -> PackageOperationType.UPGRADE
                    else -> null
                }
            }
            is PackagesTableItem.InstallablePackage -> PackageOperationType.INSTALL
        }

    @Nls
    private fun generateMessageFor(item: PackagesTableItem<*>): String? {
        val packageModel = item.packageModel

        val repoToInstall = knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(
            project = project,
            packageModel = packageModel,
            selectedVersion = item.uiPackageModel.selectedVersion.originalVersion
        ) ?: return null

        return PackageSearchBundle.message(
            "packagesearch.repository.willBeAddedOnInstall",
            repoToInstall.displayName
        )
    }

    data class ActionViewModel(
        val packageModel: PackageModel,
        val operations: Deferred<List<PackageSearchOperation<*>>>,
        val operationType: PackageOperationType?,
        @Nls val infoMessage: String?,
        val isSearchResult: Boolean
    )
}

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import com.intellij.util.ui.ColumnInfo
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageOperationType
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PackageActionsTableCellRendererAndEditor
import org.jetbrains.annotations.Nls
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

internal class ActionsColumn(
    private val operationExecutor: (List<PackageSearchOperation<*>>) -> Unit,
    private val operationFactory: PackageSearchOperationFactory
) : ColumnInfo<PackagesTableItem<*>, Any>(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.actions")) {

    var hoverItem: PackagesTableItem<*>? = null

    private var targetModules: TargetModules = TargetModules.None
    private var knownRepositoriesInTargetModules = KnownRepositories.InTargetModules.EMPTY
    private var allKnownRepositories = KnownRepositories.All.EMPTY
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
        knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
        allKnownRepositories: KnownRepositories.All
    ) {
        this.onlyStable = onlyStable
        this.targetModules = targetModules
        this.knownRepositoriesInTargetModules = knownRepositoriesInTargetModules
        this.allKnownRepositories = allKnownRepositories
    }

    override fun valueOf(item: PackagesTableItem<*>): ActionsViewModel {
        val operationType = getOperationTypeFor(item)
        return ActionsViewModel(
            item.packageModel,
            createOperationsFor(item, operationType),
            operationType,
            generateMessageFor(item),
            isSearchResult = item is PackagesTableItem.InstallablePackage,
            isHover = item == hoverItem
        )
    }

    private fun getOperationTypeFor(item: PackagesTableItem<*>): PackageOperationType? =
        when (item) {
            is PackagesTableItem.InstalledPackage -> {
                val packageModel = item.packageModel
                val currentVersion = item.selectedPackageModel.selectedVersion

                when {
                  currentVersion is PackageVersion.Missing -> PackageOperationType.SET
                  packageModel.canBeUpgraded(currentVersion, onlyStable) -> PackageOperationType.UPGRADE
                  else -> null
                }
            }
            is PackagesTableItem.InstallablePackage -> PackageOperationType.INSTALL
        }

    private fun createOperationsFor(item: PackagesTableItem<*>, operationType: PackageOperationType?): List<PackageSearchOperation<*>> {
        if (operationType == null) return emptyList()

        val packageModel = item.packageModel
        val targetVersion = packageModel.getLatestAvailableVersion(onlyStable)
            ?: return emptyList()

        val repoToInstall = knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(
            packageModel,
            targetVersion,
            allKnownRepositories
        )

        return when (operationType) {
            PackageOperationType.UPGRADE, PackageOperationType.SET -> {
                operationFactory.createChangePackageVersionOperations(
                    packageModel = packageModel as PackageModel.Installed,
                    newVersion = targetVersion,
                    targetModules = targetModules,
                    repoToInstall = repoToInstall
                )
            }
            PackageOperationType.INSTALL -> {
                operationFactory.createAddPackageOperations(
                    packageModel = packageModel as PackageModel.SearchResult,
                    version = targetVersion,
                    scope = item.selectedPackageModel.selectedScope,
                    targetModules = targetModules,
                    repoToInstall = repoToInstall
                )
            }
        }
    }

    @Nls
    private fun generateMessageFor(item: PackagesTableItem<*>): String? {
        val packageModel = item.packageModel
        val selectedVersion = item.selectedPackageModel.selectedVersion

        val repoToInstall = knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(
            packageModel,
            selectedVersion,
            allKnownRepositories
        ) ?: return null

        return PackageSearchBundle.message(
            "packagesearch.repository.willBeAddedOnInstall",
            repoToInstall.displayName
        )
    }

    data class ActionsViewModel(
        val packageModel: PackageModel,
        val operations: List<PackageSearchOperation<*>>,
        val operationType: PackageOperationType?,
        @Nls val infoMessage: String?,
        val isSearchResult: Boolean,
        val isHover: Boolean
    )
}

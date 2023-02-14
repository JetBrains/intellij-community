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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import com.intellij.util.ui.ColumnInfo
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RepositoryModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageOperationType
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.repositoryToAddWhenInstallingOrUpgrading
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.PackageManagementOperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTable
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PackageActionsTableCellRendererAndEditor
import org.jetbrains.annotations.Nls
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

internal class ActionsColumn(
    private val operationExecutor: (PackageManagementOperationExecutor.() -> Unit) -> Unit
) : ColumnInfo<PackagesTableItem<*>, Any>(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.actions")) {

    private var targetModules: TargetModules = TargetModules.None
    private var knownRepositoriesInTargetModules: Map<PackageSearchModule, List<RepositoryModel>> = emptyMap()
    private val allKnownRepositories: List<RepositoryModel> = emptyList()
    private var onlyStable = false

    private val cellRendererAndEditor = PackageActionsTableCellRendererAndEditor {
        operationExecutor(it)
    }

    override fun getRenderer(item: PackagesTableItem<*>): TableCellRenderer = cellRendererAndEditor

    override fun getEditor(item: PackagesTableItem<*>): TableCellEditor = cellRendererAndEditor

    override fun isCellEditable(item: PackagesTableItem<*>) = getOperationTypeFor(item) != null

    fun updateData(viewModel: PackagesTable.ViewModel) {
        this.onlyStable = viewModel.onlyStable
        this.targetModules = viewModel.targetModules
        this.knownRepositoriesInTargetModules = viewModel.knownRepositoriesInTargetModules
    }

    override fun valueOf(item: PackagesTableItem<*>): ActionViewModel {
        val operationType = getOperationTypeFor(item)
        return ActionViewModel(
            item.packageModel,
            operationType,
            item.uiPackageModel.packageOperations.primaryOperations,
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
        val repoToInstall = item.packageModel
            .repositoryToAddWhenInstallingOrUpgrading(targetModules, knownRepositoriesInTargetModules, allKnownRepositories)
        if (repoToInstall.isEmpty()) return null
        return PackageSearchBundle.message(
            "packagesearch.repository.willBeAddedOnInstall.withModules",
            repoToInstall.values.first().displayName,
            repoToInstall.keys.joinToString { it.name }
        )
    }

    data class ActionViewModel(
        val packageModel: PackageModel,
        val operationType: PackageOperationType?,
        val operations: PackageManagementOperationExecutor.() -> Unit,
        @Nls val infoMessage: String?,
        val isSearchResult: Boolean
    )
}

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel

internal fun computePackagesTableItems(
    project: Project,
    packages: List<UiPackageModel<*>>,
    targetModules: TargetModules,
): PackagesTable.ViewModel.TableItems {
    if (targetModules is TargetModules.None) {
        return PackagesTable.ViewModel.TableItems.EMPTY
    }

    val modules = targetModules.modules

    val mixedBuildSystems = targetModules.isMixedBuildSystems
    val defaultScope = if (!mixedBuildSystems) {
        PackageScope.from(modules.first().projectModule.moduleType.defaultScope(project))
    } else {
        PackageScope.Missing
    }

    val items = packages.map { uiPackageModel ->
        when (uiPackageModel) {
            is UiPackageModel.Installed -> {
                val scopes = uiPackageModel.packageModel.usageInfo
                    .flatMap { usageInfo ->
                        val availableModules = usageInfo.projectModule.availableScopes.map { PackageScope.from(it) }
                        availableModules + usageInfo.userDefinedScopes
                    }
                    .distinct()
                    .sorted()

                PackagesTableItem.InstalledPackage(uiPackageModel, defaultScope, scopes)
            }
            is UiPackageModel.SearchResult -> {
                val scopes = targetModules.modules.flatMap {
                    val userScopes = it.projectModule.moduleType.userDefinedScopes(project)
                        .map { rawScope -> PackageScope.from(rawScope) }
                    val availableScopes = it.projectModule.availableScopes.map { PackageScope.from(it) }
                    availableScopes + userScopes
                }.distinct().sorted()

                PackagesTableItem.InstallablePackage(uiPackageModel, defaultScope, scopes)
            }
        }
    }
    return PackagesTable.ViewModel.TableItems(items)
}

private fun findItemToSelect(
    items: List<PackagesTableItem<out PackageModel>>,
    uiPackageModel: UiPackageModel<*>?
): Int? {
    if (uiPackageModel == null) return null

    // Item index -> likelihood [0-10)
    val selectionCandidates = mutableMapOf<Int, Int>()

    for ((index, item) in items.withIndex()) {
        when {
            item.packageModel == uiPackageModel.packageModel -> selectionCandidates += (index to 9)
            item.packageModel.identifier == uiPackageModel.packageModel.identifier
                && item.uiPackageModel.selectedScope == uiPackageModel.selectedScope -> selectionCandidates += (index to 7)
            item.packageModel.identifier == uiPackageModel.packageModel.identifier
                && item.uiPackageModel.selectedVersion == uiPackageModel.selectedVersion
                && item.uiPackageModel.selectedScope == uiPackageModel.selectedScope -> selectionCandidates += (index to 7)
            item.packageModel.identifier == uiPackageModel.packageModel.identifier
                && item.uiPackageModel.selectedVersion == uiPackageModel.selectedVersion -> selectionCandidates += (index to 7)
            item.packageModel.identifier == uiPackageModel.packageModel.identifier -> selectionCandidates += (index to 6)
        }
    }

    return selectionCandidates.entries.maxByOrNull { it.value }?.key
}

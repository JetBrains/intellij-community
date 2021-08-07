package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug

internal fun computePackagesTableItems(
    project: Project,
    packages: List<UiPackageModel<*>>,
    selectedPackage: UiPackageModel<*>?,
    targetModules: TargetModules,
    traceInfo: TraceInfo
): PackagesTable.ViewModel.TableItems {
    logDebug(traceInfo, "PackagesTable#computeDisplayItems()") { "Creating item models for ${packages.size} item(s)" }

    if (targetModules is TargetModules.None) {
        logDebug(traceInfo, "PackagesTable#computeDisplayItems()") {
            "Current target modules is None, no items models to compute"
        }
        return PackagesTable.ViewModel.TableItems.EMPTY
    }
    logDebug(traceInfo, "PackagesTable#computeDisplayItems()") {
        "Current target modules value: ${targetModules.javaClass.simpleName} " +
            "${targetModules.modules.map { it.projectModule.name }}"
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
            is UiPackageModel.Installed -> PackagesTableItem.InstalledPackage(uiPackageModel, defaultScope)
            is UiPackageModel.SearchResult -> PackagesTableItem.InstallablePackage(uiPackageModel, defaultScope)
        }
    }
    val selectedItemIndex = findItemToSelect(items, selectedPackage)
    return PackagesTable.ViewModel.TableItems(items, indexToSelect = selectedItemIndex)
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

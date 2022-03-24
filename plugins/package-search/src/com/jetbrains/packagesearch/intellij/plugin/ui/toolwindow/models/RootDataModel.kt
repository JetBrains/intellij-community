package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesHeaderData
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo

internal data class RootDataModel(
    val moduleModels: List<ModuleModel>,
    val packageModels: List<UiPackageModel<*>>,
    val packagesToUpdate: PackagesToUpgrade,
    val headerData: PackagesHeaderData,
    val targetModules: TargetModules,
    val allKnownRepositories: KnownRepositories.All,
    val knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
    val selectedPackage: UiPackageModel<*>?,
    val filterOptions: FilterOptions,
    val traceInfo: TraceInfo,
    val searchQuery: String
) {

//    companion object {
//
//        val EMPTY = RootDataModel(
//            moduleModels = emptyList(),
//            packageModels = emptyList(),
//            packagesToUpdate = PackagesToUpgrade.EMPTY,
//            headerData = PackagesHeaderData.EMPTY,
//            targetModules = TargetModules.None,
//            allKnownRepositories = KnownRepositories.All.EMPTY,
//            knownRepositoriesInTargetModules = KnownRepositories.InTargetModules.EMPTY,
//            selectedPackage = null,
//            filterOptions = FilterOptions(),
//            traceInfo = TraceInfo.EMPTY,
//            searchQuery = ""
//        )
//    }
}

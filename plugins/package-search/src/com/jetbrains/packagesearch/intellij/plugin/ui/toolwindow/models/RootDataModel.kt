package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesHeaderData
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo

internal data class RootDataModel(
    val projectModules: List<ModuleModel>,
    val packageModels: List<PackageModel>,
    val knownRepositoryModels: List<RepositoryModel>,
    val headerData: PackagesHeaderData,
    val targetModules: TargetModules,
    val selectedPackage: SelectedPackageModel<*>?,
    val filterOptions: FilterOptions,
    val traceInfo: TraceInfo
) {

    companion object {

        val EMPTY = RootDataModel(
            projectModules = emptyList(),
            packageModels = emptyList(),
            knownRepositoryModels = emptyList(),
            headerData = PackagesHeaderData.EMPTY,
            targetModules = TargetModules.None,
            selectedPackage = null,
            filterOptions = FilterOptions(),
            traceInfo = TraceInfo.EMPTY
        )
    }
}

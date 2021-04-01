package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesHeaderData
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo

internal data class RootData(
    val projectModules: List<ModuleModel>,
    val packageModels: List<PackageModel>,
    val installedKnownRepositories: List<RepositoryModel>,
    val headerData: PackagesHeaderData,
    val targetModules: TargetModules,
    val selectedPackage: SelectedPackageModel<*>?,
    val filterOptions: FilterOptions,
    val traceInfo: TraceInfo
) {

    companion object {

        val EMPTY = RootData(
            projectModules = emptyList(),
            packageModels = emptyList(),
            installedKnownRepositories = emptyList(),
            headerData = PackagesHeaderData.EMPTY,
            targetModules = TargetModules.None,
            selectedPackage = null,
            filterOptions = FilterOptions(),
            traceInfo = TraceInfo.EMPTY
        )
    }
}

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.module.Module
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion

internal data class PackagesToUpgrade(
    val upgradesByModule: Map<Module, Set<PackageUpgradeInfo>>
) {

    val allUpdates by lazy { upgradesByModule.values.flatten() }

    fun getUpdatesForModule(moduleModel: ModuleModel) =
        upgradesByModule[moduleModel.projectModule.nativeModule]?.toList() ?: emptyList()

    data class PackageUpgradeInfo(
        val packageModel: PackageModel.Installed,
        val usageInfo: DependencyUsageInfo,
        val targetVersion: NormalizedPackageVersion<PackageVersion.Named>,
        val computeUpgradeOperationsForSingleModule: List<PackageSearchOperation<*>>
    )

    companion object {

        val EMPTY = PackagesToUpgrade(emptyMap())
    }
}

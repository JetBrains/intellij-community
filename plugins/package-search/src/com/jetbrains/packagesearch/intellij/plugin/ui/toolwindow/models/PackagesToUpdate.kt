package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.module.Module

internal data class PackagesToUpdate(
    val updatesByModule: Map<Module, Set<PackageUpdateInfo>>
) {

    val allUpdates by lazy { updatesByModule.values.flatten() }

    data class PackageUpdateInfo(
        val packageModel: PackageModel.Installed,
        val usageInfo: DependencyUsageInfo,
        val targetVersion: PackageVersion.Named
    )

    companion object {

        val EMPTY = PackagesToUpdate(emptyMap())
    }
}

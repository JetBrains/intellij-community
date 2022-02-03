package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.openapi.module.Module
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackagesToUpgrade
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.upgradeCandidateVersionOrNull
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.PackageVersionNormalizer
import com.jetbrains.packagesearch.intellij.plugin.util.logError
import com.jetbrains.packagesearch.packageversionutils.PackageVersionUtils

internal suspend fun computePackageUpgrades(
    installedPackages: List<PackageModel.Installed>,
    onlyStable: Boolean,
    normalizer: PackageVersionNormalizer
): PackagesToUpgrade {
    val updatesByModule = mutableMapOf<Module, MutableSet<PackagesToUpgrade.PackageUpgradeInfo>>()
    for (installedPackageModel in installedPackages) {
        val availableVersions = installedPackageModel.getAvailableVersions(onlyStable)
        if (installedPackageModel.remoteInfo == null || availableVersions.isEmpty()) continue

        for (usageInfo in installedPackageModel.usageInfo) {
            val currentVersion = usageInfo.version
            if (currentVersion !is PackageVersion.Named) continue

            val normalizedPackageVersion = runCatching { NormalizedPackageVersion.parseFrom(currentVersion, normalizer) }
                .onFailure { logError(throwable = it) { "Unable to normalize version: $currentVersion" } }
                .getOrNull() ?: continue

            val upgradeVersion = PackageVersionUtils.upgradeCandidateVersionOrNull(normalizedPackageVersion, availableVersions)
            if (upgradeVersion != null && upgradeVersion.originalVersion is PackageVersion.Named) {
                @Suppress("UNCHECKED_CAST") // The if guards us against cast errors
                updatesByModule.getOrPut(usageInfo.projectModule.nativeModule) { mutableSetOf() } +=
                    PackagesToUpgrade.PackageUpgradeInfo(
                        installedPackageModel,
                        usageInfo,
                        upgradeVersion as NormalizedPackageVersion<PackageVersion.Named>
                    )
            }
        }
    }

    return PackagesToUpgrade(updatesByModule)
}

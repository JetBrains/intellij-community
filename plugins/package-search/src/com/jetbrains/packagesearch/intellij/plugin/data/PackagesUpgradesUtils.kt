package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.openapi.module.Module
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackagesToUpgrade
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.upgradeCandidateVersionOrNull
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.PackageVersionNormalizer
import com.jetbrains.packagesearch.intellij.plugin.util.logError
import com.jetbrains.packagesearch.packageversionutils.PackageVersionUtils

internal suspend fun computePackageUpgrades(
    installedPackages: List<PackageModel.Installed>,
    onlyStable: Boolean,
    normalizer: PackageVersionNormalizer,
    repos: KnownRepositories.All,
    nativeModulesMap: Map<ProjectModule, ModuleModel>
): PackagesToUpgrade {
    val operationFactory = PackageSearchOperationFactory()
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
                val moduleModel = nativeModulesMap.getValue(usageInfo.projectModule)
                @Suppress("UNCHECKED_CAST") // The if guards us against cast errors
                updatesByModule.getOrPut(usageInfo.projectModule.nativeModule) { mutableSetOf() } +=
                    PackagesToUpgrade.PackageUpgradeInfo(
                        packageModel = installedPackageModel,
                        usageInfo = usageInfo,
                        targetVersion = upgradeVersion as NormalizedPackageVersion<PackageVersion.Named>,
                        computeUpgradeOperationsForSingleModule = computeUpgradeOperationsForSingleModule(
                            packageModel = installedPackageModel,
                            targetModule = moduleModel,
                            knownRepositoriesInTargetModules = repos.filterOnlyThoseUsedIn(TargetModules.One(moduleModel)),
                            onlyStable = onlyStable,
                            operationFactory = operationFactory
                        )
                    )
            }
        }
    }

    return PackagesToUpgrade(updatesByModule)
}

internal suspend inline fun computeUpgradeOperationsForSingleModule(
    packageModel: PackageModel.Installed,
    targetModule: ModuleModel,
    knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
    onlyStable: Boolean,
    operationFactory: PackageSearchOperationFactory = PackageSearchOperationFactory()
): List<PackageSearchOperation<*>> {
    val availableVersions = packageModel.getAvailableVersions(onlyStable)

    val upgradeVersion = when {
        availableVersions.isNotEmpty() -> {
            val currentVersion = packageModel.latestInstalledVersion
            PackageVersionUtils.upgradeCandidateVersionOrNull(currentVersion, availableVersions)
        }
        else -> null
    }

    return upgradeVersion?.let {
        operationFactory.computeUpgradeActionsFor(
            project = targetModule.projectModule.nativeModule.project,
            packageModel = packageModel,
            moduleModel = targetModule,
            knownRepositories = knownRepositoriesInTargetModules,
            targetVersion = it
        )
    } ?: emptyList()
}

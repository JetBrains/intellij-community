package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageOperationType
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.logWarn
import com.jetbrains.packagesearch.packageversionutils.PackageVersionUtils

internal inline fun <reified T : PackageModel> computeActionsFor(
    packageModel: T,
    targetModules: TargetModules,
    knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
    onlyStable: Boolean
): PackageOperations {
    val operationFactory = PackageSearchOperationFactory()

    val availableVersions = packageModel.getAvailableVersions(onlyStable)

    val upgradeVersion = when (packageModel) {
        is PackageModel.Installed -> {
            val currentVersion = packageModel.latestInstalledVersion
            PackageVersionUtils.upgradeCandidateVersionOrNull(currentVersion, availableVersions)
        }
        else -> null
    }

    val highestAvailableVersion = PackageVersionUtils.highestSensibleVersionByNameOrNull(availableVersions)

    val primaryOperationType = decidePrimaryOperationTypeFor(packageModel, upgradeVersion)

    val primaryOperations = mutableListOf<PackageSearchOperation<*>>()
    val removeOperations = mutableListOf<PackageSearchOperation<*>>()
    targetModules.modules.forEach { moduleModel ->
        removeOperations += operationFactory.computeRemoveActionsFor(packageModel, moduleModel)

        val project = moduleModel.projectModule.nativeModule.project
        primaryOperations += when (primaryOperationType) {
            PackageOperationType.INSTALL -> {
                if (highestAvailableVersion != null) {
                    operationFactory.computeInstallActionsFor(
                        packageModel = packageModel,
                        moduleModel = moduleModel,
                        defaultScope = targetModules.defaultScope(project),
                        knownRepositories = knownRepositoriesInTargetModules,
                        targetVersion = highestAvailableVersion
                    )
                } else {
                    logWarn(
                        "Trying to compute install actions for '${packageModel.identifier.rawValue}' into '${moduleModel.projectModule.name}' " +
                            "but there's no known version to install"
                    )
                    emptyList()
                }
            }
            PackageOperationType.UPGRADE, PackageOperationType.SET -> {
                if (upgradeVersion != null) {
                    operationFactory.computeUpgradeActionsFor(
                        packageModel = packageModel,
                        moduleModel = moduleModel,
                        knownRepositories = knownRepositoriesInTargetModules,
                        targetVersion = upgradeVersion
                    )
                } else {
                    logWarn(
                        "Trying to compute upgrade/set actions for '${packageModel.identifier.rawValue}' into '${moduleModel.projectModule.name}' " +
                            "but there's no version to upgrade to"
                    )
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }

    val repoToInstall = when (primaryOperationType) {
        PackageOperationType.INSTALL -> highestAvailableVersion?.let {
            knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(packageModel, it.originalVersion)
        }
        PackageOperationType.UPGRADE -> upgradeVersion?.let {
            knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(packageModel, it.originalVersion)
        }
        else -> null
    }

    return PackageOperations(
        targetModules = targetModules,
        primaryOperations = primaryOperations,
        removeOperations = removeOperations,
        targetVersion = if (primaryOperationType == PackageOperationType.INSTALL) highestAvailableVersion else upgradeVersion,
        primaryOperationType = primaryOperationType,
        repoToAddWhenInstalling = repoToInstall
    )
}

private fun <T : PackageModel> decidePrimaryOperationTypeFor(
    packageModel: T,
    targetVersion: NormalizedPackageVersion<*>?
): PackageOperationType? =
    when (packageModel) {
        is PackageModel.SearchResult -> PackageOperationType.INSTALL
        is PackageModel.Installed -> {
            when {
                targetVersion == null -> null
                packageModel.usageInfo.any { it.version is PackageVersion.Missing } -> PackageOperationType.SET
                else -> PackageOperationType.UPGRADE
            }
        }
        else -> error("Unsupported package model type: ${packageModel::class.simpleName}")
    }

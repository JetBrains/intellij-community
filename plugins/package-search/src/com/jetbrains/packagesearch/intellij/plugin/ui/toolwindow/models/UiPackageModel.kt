package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageOperationType
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.packageversionutils.PackageVersionUtils

internal sealed class UiPackageModel<T : PackageModel> {

    abstract val packageModel: T
    abstract val declaredScopes: List<PackageScope>
    abstract val defaultScope: PackageScope
    abstract val selectedVersion: PackageVersion
    abstract val selectedScope: PackageScope
    abstract val mixedBuildSystemTargets: Boolean
    abstract val packageOperations: PackageOperations
    abstract val sortedVersions: List<PackageVersion>

    val identifier
        get() = packageModel.identifier

    data class Installed(
        override val packageModel: PackageModel.Installed,
        override val declaredScopes: List<PackageScope>,
        override val defaultScope: PackageScope,
        override val selectedVersion: PackageVersion,
        override val selectedScope: PackageScope,
        override val mixedBuildSystemTargets: Boolean,
        override val packageOperations: PackageOperations,
        override val sortedVersions: List<PackageVersion>
    ) : UiPackageModel<PackageModel.Installed>()

    data class SearchResult(
        override val packageModel: PackageModel.SearchResult,
        override val declaredScopes: List<PackageScope>,
        override val defaultScope: PackageScope,
        override val selectedVersion: PackageVersion,
        override val selectedScope: PackageScope,
        override val mixedBuildSystemTargets: Boolean,
        override val packageOperations: PackageOperations,
        override val sortedVersions: List<PackageVersion>
    ) : UiPackageModel<PackageModel.SearchResult>()
}

internal fun PackageModel.Installed.toUiPackageModel(
    targetModules: TargetModules,
    project: Project,
    knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
    onlyStable: Boolean
): UiPackageModel.Installed {
    val declaredScopes = declaredScopes(targetModules)
    val defaultScope = targetModules.defaultScope(project)
    return UiPackageModel.Installed(
        packageModel = this,
        declaredScopes = declaredScopes,
        defaultScope = defaultScope,
        selectedVersion = getLatestInstalledVersion(),
        selectedScope = declaredScopes.firstOrNull() ?: defaultScope,
        mixedBuildSystemTargets = targetModules.isMixedBuildSystems,
        packageOperations = computeActionsFor(this, targetModules, knownRepositoriesInTargetModules, onlyStable),
        sortedVersions = PackageVersionUtils.sortWithHeuristicsDescending(getAvailableVersions(onlyStable))
    )
}

private fun PackageModel.Installed.declaredScopes(targetModules: TargetModules): List<PackageScope> =
    if (targetModules.modules.isNotEmpty()) {
        findUsagesIn(targetModules.modules).map { it.scope }
    } else {
        usageInfo.map { it.scope }
    }
        .distinct()
        .sorted()

internal fun PackageModel.SearchResult.toUiPackageModel(
    targetModules: TargetModules,
    project: Project,
    searchResultUiState: SearchResultUiState?,
    knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
    onlyStable: Boolean,
) = this.toUiPackageModel(
    declaredScopes = targetModules.declaredScopes(project),
    defaultScope = targetModules.defaultScope(project),
    mixedBuildSystems = targetModules.isMixedBuildSystems,
    searchResultUiState = searchResultUiState,
    onlyStable = onlyStable,
    targetModules = targetModules,
    knownRepositoriesInTargetModules = knownRepositoriesInTargetModules
)

private fun TargetModules.declaredScopes(project: Project): List<PackageScope> =
    modules.flatMap { it.projectModule.moduleType.userDefinedScopes(project) }
        .map { rawScope -> PackageScope.from(rawScope) }
        .distinct()
        .sorted()

private fun TargetModules.defaultScope(project: Project): PackageScope =
    if (!isMixedBuildSystems) {
        PackageScope.from(modules.first().projectModule.moduleType.defaultScope(project))
    } else {
        PackageScope.Missing
    }

internal fun PackageModel.SearchResult.toUiPackageModel(
    declaredScopes: List<PackageScope>,
    defaultScope: PackageScope,
    mixedBuildSystems: Boolean,
    searchResultUiState: SearchResultUiState?,
    onlyStable: Boolean,
    targetModules: TargetModules,
    knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
): UiPackageModel.SearchResult =
    UiPackageModel.SearchResult(
        packageModel = this,
        declaredScopes = declaredScopes,
        defaultScope = defaultScope,
        selectedVersion = searchResultUiState?.selectedVersion
            ?: PackageVersionUtils.highestVersionByName(getAvailableVersions(onlyStable)),
        selectedScope = searchResultUiState?.selectedScope
            ?: defaultScope,
        mixedBuildSystemTargets = mixedBuildSystems,
        packageOperations = computeActionsFor(this, targetModules, knownRepositoriesInTargetModules, onlyStable),
        sortedVersions = PackageVersionUtils.sortWithHeuristicsDescending(getAvailableVersions(onlyStable))
    )

private inline fun <reified T : PackageModel> computeActionsFor(
    packageModel: T,
    targetModules: TargetModules,
    knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
    onlyStable: Boolean
): PackageOperations {
    val operationFactory = PackageSearchOperationFactory()

    val availableVersions = packageModel.getAvailableVersions(onlyStable)

    val upgradeToVersion = if (packageModel is PackageModel.Installed) {
        val currentVersion = packageModel.getLatestInstalledVersion()
        PackageVersionUtils.upgradeCandidateVersionOrNull(currentVersion, availableVersions)
    } else {
        null
    }
    val highestAvailableVersion = if (availableVersions.isNotEmpty()) {
        PackageVersionUtils.highestSensibleVersionByNameOrNull(availableVersions)
    } else {
        null
    }

    val primaryOperationType = decidePrimaryOperationTypeFor(packageModel, upgradeToVersion)

    val primaryOperations = mutableListOf<PackageSearchOperation<*>>()
    val removeOperations = mutableListOf<PackageSearchOperation<*>>()
    targetModules.modules.forEach { moduleModel ->
        removeOperations += operationFactory.computeRemoveActionsFor(packageModel, moduleModel)

        val project = moduleModel.projectModule.nativeModule.project
        primaryOperations += when (primaryOperationType) {
            PackageOperationType.INSTALL -> operationFactory.computeInstallActionsFor(
                packageModel = packageModel,
                moduleModel = moduleModel,
                defaultScope = targetModules.defaultScope(project),
                knownRepositories = knownRepositoriesInTargetModules,
                onlyStable = onlyStable
            )
            PackageOperationType.UPGRADE -> operationFactory.computeUpgradeActionsFor(
                packageModel = packageModel,
                moduleModel = moduleModel,
                knownRepositories = knownRepositoriesInTargetModules,
                onlyStable = onlyStable
            )
            PackageOperationType.SET -> operationFactory.computeUpgradeActionsFor(
                packageModel = packageModel,
                moduleModel = moduleModel,
                knownRepositories = knownRepositoriesInTargetModules,
                onlyStable = onlyStable
            )
            else -> emptyList()
        }
    }

    val repoToInstall = when (primaryOperationType) {
        PackageOperationType.INSTALL -> highestAvailableVersion?.let {
            knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(packageModel, it)
        }
        PackageOperationType.UPGRADE -> upgradeToVersion?.let {
            knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(packageModel, it)
        }
        else -> null
    }

    return PackageOperations(
        targetModules = targetModules,
        primaryOperations = primaryOperations,
        removeOperations = removeOperations,
        targetVersion = highestAvailableVersion,
        primaryOperationType = primaryOperationType,
        repoToAddWhenInstalling = repoToInstall
    )
}

private fun <T : PackageModel> decidePrimaryOperationTypeFor(
    packageModel: T,
    targetVersion: PackageVersion.Named?
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

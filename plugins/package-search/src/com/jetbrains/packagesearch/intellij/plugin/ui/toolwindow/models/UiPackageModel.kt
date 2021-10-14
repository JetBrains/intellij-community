package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion

internal sealed class UiPackageModel<T : PackageModel> {

    abstract val packageModel: T
    abstract val declaredScopes: List<PackageScope>
    abstract val userDefinedScopes: List<PackageScope>
    abstract val defaultScope: PackageScope
    abstract val selectedVersion: PackageVersion
    abstract val selectedScope: PackageScope
    abstract val mixedBuildSystemTargets: Boolean
    abstract val packageOperations: PackageOperations
    abstract val sortedVersions: List<NormalizedPackageVersion>

    val identifier
        get() = packageModel.identifier

    data class Installed(
        override val packageModel: PackageModel.Installed,
        override val declaredScopes: List<PackageScope>,
        override val userDefinedScopes: List<PackageScope>,
        override val defaultScope: PackageScope,
        override val selectedVersion: PackageVersion,
        override val selectedScope: PackageScope,
        override val mixedBuildSystemTargets: Boolean,
        override val packageOperations: PackageOperations,
        override val sortedVersions: List<NormalizedPackageVersion>
    ) : UiPackageModel<PackageModel.Installed>()

    data class SearchResult(
        override val packageModel: PackageModel.SearchResult,
        override val declaredScopes: List<PackageScope>,
        override val userDefinedScopes: List<PackageScope>,
        override val defaultScope: PackageScope,
        override val selectedVersion: PackageVersion,
        override val selectedScope: PackageScope,
        override val mixedBuildSystemTargets: Boolean,
        override val packageOperations: PackageOperations,
        override val sortedVersions: List<NormalizedPackageVersion>
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
    val selectedVersion = getLatestInstalledVersion()
    val normalizedPackageVersion = selectedVersion.asNamedOrNull()
        ?.let { NormalizedPackageVersion.parseFrom(it) }

    val sortedVersions = if (normalizedPackageVersion != null) {
        getAvailableVersions(onlyStable) + normalizedPackageVersion
    } else {
        getAvailableVersions(onlyStable)
    }
        .distinct()
        .sortedDescending()

    return UiPackageModel.Installed(
        packageModel = this,
        declaredScopes = declaredScopes,
        userDefinedScopes = userDefinedScopes(project),
        defaultScope = defaultScope,
        selectedVersion = selectedVersion,
        selectedScope = declaredScopes.firstOrNull() ?: defaultScope,
        mixedBuildSystemTargets = targetModules.isMixedBuildSystems,
        packageOperations = computeActionsFor(this, targetModules, knownRepositoriesInTargetModules, onlyStable),
        sortedVersions = sortedVersions
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

private fun PackageModel.Installed.userDefinedScopes(project: Project) =
    usageInfo.asSequence()
        .map { it.projectModule }
        .distinct()
        .flatMap { it.moduleType.userDefinedScopes(project) }
        .map { PackageScope.from(it) }
        .toList()

private fun PackageVersion.asNamedOrNull(): PackageVersion.Named? =
    if (this is PackageVersion.Named) this else null

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
        userDefinedScopes = emptyList(),
        selectedVersion = searchResultUiState?.selectedVersion
            ?: getAvailableVersions(onlyStable).firstOrNull()?.originalVersion ?: PackageVersion.Missing,
        selectedScope = searchResultUiState?.selectedScope
            ?: defaultScope,
        mixedBuildSystemTargets = mixedBuildSystems,
        packageOperations = computeActionsFor(this, targetModules, knownRepositoriesInTargetModules, onlyStable),
        sortedVersions = getAvailableVersions(onlyStable).sortedDescending()
    )

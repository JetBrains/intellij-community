package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import kotlinx.coroutines.CoroutineScope

internal sealed class UiPackageModel<T : PackageModel> {

    abstract val packageModel: T
    abstract val declaredScopes: List<PackageScope>
    abstract val userDefinedScopes: List<PackageScope>
    abstract val defaultScope: PackageScope
    abstract val defaultVersion: NormalizedPackageVersion<*>
    abstract val selectedVersion: NormalizedPackageVersion<*>
    abstract val selectedScope: PackageScope
    abstract val mixedBuildSystemTargets: Boolean
    abstract val packageOperations: PackageOperations
    abstract val sortedVersions: List<NormalizedPackageVersion<*>>

    val identifier
        get() = packageModel.identifier

    data class Installed(
        override val packageModel: PackageModel.Installed,
        override val declaredScopes: List<PackageScope>,
        override val userDefinedScopes: List<PackageScope>,
        override val defaultVersion: NormalizedPackageVersion<*>,
        override val defaultScope: PackageScope,
        override val selectedVersion: NormalizedPackageVersion<*>,
        override val selectedScope: PackageScope,
        override val mixedBuildSystemTargets: Boolean,
        override val packageOperations: PackageOperations,
        override val sortedVersions: List<NormalizedPackageVersion<*>>
    ) : UiPackageModel<PackageModel.Installed>()

    data class SearchResult(
        override val packageModel: PackageModel.SearchResult,
        override val declaredScopes: List<PackageScope>,
        override val userDefinedScopes: List<PackageScope>,
        override val defaultVersion: NormalizedPackageVersion<*>,
        override val defaultScope: PackageScope,
        override val selectedVersion: NormalizedPackageVersion<*>,
        override val selectedScope: PackageScope,
        override val mixedBuildSystemTargets: Boolean,
        override val packageOperations: PackageOperations,
        override val sortedVersions: List<NormalizedPackageVersion<*>>
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

    val sortedVersions = (getAvailableVersions(onlyStable) + latestInstalledVersion)
        .distinct()
        .sortedDescending()

    return UiPackageModel.Installed(
        packageModel = this,
        declaredScopes = declaredScopes,
        userDefinedScopes = userDefinedScopes(project),
        defaultVersion = sortedVersions.first(),
        defaultScope = defaultScope,
        selectedVersion = latestInstalledVersion,
        selectedScope = declaredScopes.firstOrNull() ?: defaultScope,
        mixedBuildSystemTargets = targetModules.isMixedBuildSystems,
        packageOperations = project.lifecycleScope.computeActionsAsync(project, this, targetModules, knownRepositoriesInTargetModules, onlyStable),
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

internal fun PackageModel.SearchResult.toUiPackageModel(
    targetModules: TargetModules,
    project: Project,
    searchResultUiState: SearchResultUiState?,
    knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
    onlyStable: Boolean
) = toUiPackageModel(
    project = project,
    declaredScopes = targetModules.declaredScopes(project),
    defaultScope = targetModules.defaultScope(project),
    mixedBuildSystems = targetModules.isMixedBuildSystems,
    searchResultUiState = searchResultUiState,
    onlyStable = onlyStable,
    targetModules = targetModules,
    knownRepositoriesInTargetModules = knownRepositoriesInTargetModules,
    coroutineScope = project.lifecycleScope
)

internal fun PackageModel.SearchResult.toUiPackageModel(
    project: Project,
    declaredScopes: List<PackageScope>,
    defaultScope: PackageScope,
    mixedBuildSystems: Boolean,
    searchResultUiState: SearchResultUiState?,
    onlyStable: Boolean,
    targetModules: TargetModules,
    knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
    coroutineScope: CoroutineScope
): UiPackageModel.SearchResult {
    val sortedVersions = getAvailableVersions(onlyStable).sortedDescending()
    val selectedVersion = searchResultUiState?.selectedVersion ?: sortedVersions.first()
    val selectedScope = searchResultUiState?.selectedScope ?: defaultScope
    return UiPackageModel.SearchResult(
        packageModel = this,
        declaredScopes = declaredScopes,
        userDefinedScopes = emptyList(),
        defaultVersion = sortedVersions.first(),
        defaultScope = defaultScope,
        selectedVersion = selectedVersion,
        selectedScope = selectedScope,
        mixedBuildSystemTargets = mixedBuildSystems,
        packageOperations = coroutineScope.computeActionsAsync(
            project = project,
            packageModel = this,
            targetModules = targetModules,
            knownRepositoriesInTargetModules = knownRepositoriesInTargetModules,
            onlyStable = onlyStable,
            selectedScope = selectedScope,
            selectedVersion = selectedVersion
        ),
        sortedVersions = sortedVersions
    )
}

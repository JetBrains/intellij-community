package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.project.Project

internal sealed class UiPackageModel<T : PackageModel> {

    abstract val packageModel: T
    abstract val declaredScopes: List<PackageScope>
    abstract val defaultScope: PackageScope
    abstract val selectedVersion: PackageVersion
    abstract val selectedScope: PackageScope
    abstract val mixedBuildSystemTargets: Boolean

    val identifier
        get() = packageModel.identifier

    data class Installed(
        override val packageModel: PackageModel.Installed,
        override val declaredScopes: List<PackageScope>,
        override val defaultScope: PackageScope,
        override val selectedVersion: PackageVersion,
        override val selectedScope: PackageScope,
        override val mixedBuildSystemTargets: Boolean
    ) : UiPackageModel<PackageModel.Installed>()

    data class SearchResult(
        override val packageModel: PackageModel.SearchResult,
        override val declaredScopes: List<PackageScope>,
        override val defaultScope: PackageScope,
        override val selectedVersion: PackageVersion,
        override val selectedScope: PackageScope,
        override val mixedBuildSystemTargets: Boolean
    ) : UiPackageModel<PackageModel.SearchResult>()

    companion object Factory {

        fun from(
            packageModel: PackageModel,
            selectedVersion: PackageVersion,
            selectedScope: PackageScope,
            mixedBuildSystemTargets: Boolean,
            targetModules: TargetModules,
            project: Project
        ): UiPackageModel<*> =
            when (packageModel) {
                is PackageModel.Installed -> Installed(
                    packageModel = packageModel,
                    declaredScopes = packageModel.declaredScopes(targetModules),
                    defaultScope = targetModules.defaultScope(project),
                    selectedVersion = selectedVersion,
                    selectedScope = selectedScope,
                    mixedBuildSystemTargets = mixedBuildSystemTargets
                )
                is PackageModel.SearchResult -> SearchResult(
                    packageModel = packageModel,
                    declaredScopes = targetModules.declaredScopes(project),
                    defaultScope = targetModules.defaultScope(project),
                    selectedVersion = selectedVersion,
                    selectedScope = selectedScope,
                    mixedBuildSystemTargets = mixedBuildSystemTargets
                )
            }
    }
}

internal fun PackageModel.Installed.toUiPackageModel(targetModules: TargetModules, project: Project): UiPackageModel.Installed =
   toUiPackageModel(
       declaredScopes = declaredScopes(targetModules),
       defaultScope = targetModules.defaultScope(project),
       mixedBuildSystems = targetModules.isMixedBuildSystems
   )

private fun PackageModel.Installed.declaredScopes(targetModules: TargetModules): List<PackageScope> =
    if (targetModules.modules.isNotEmpty()) {
        findUsagesIn(targetModules.modules).map { it.scope }
    } else {
        usageInfo.map { it.scope }
    }
        .distinct()
        .sorted()

internal fun PackageModel.Installed.toUiPackageModel(
    declaredScopes: List<PackageScope>,
    defaultScope: PackageScope,
    mixedBuildSystems: Boolean
): UiPackageModel.Installed =
    UiPackageModel.Installed(
        packageModel = this,
        declaredScopes = declaredScopes,
        defaultScope = defaultScope,
        selectedVersion = getLatestInstalledVersion(),
        selectedScope = declaredScopes.firstOrNull() ?: defaultScope,
        mixedBuildSystemTargets = mixedBuildSystems,
    )

internal fun PackageModel.SearchResult.toUiPackageModel(
    onlyStable: Boolean,
    targetModules: TargetModules,
    project: Project,
    searchResultUiState: SearchResultUiState?
) = toUiPackageModel(
    onlyStable = onlyStable,
    declaredScopes = targetModules.declaredScopes(project),
    defaultScope = targetModules.defaultScope(project),
    mixedBuildSystems = targetModules.isMixedBuildSystems,
    searchResultUiState = searchResultUiState
)

private fun TargetModules.declaredScopes(project: Project): List<PackageScope> =
    modules.flatMap { it.projectModule.moduleType.scopes(project) }
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
    onlyStable: Boolean,
    declaredScopes: List<PackageScope>,
    defaultScope: PackageScope,
    mixedBuildSystems: Boolean,
    searchResultUiState: SearchResultUiState?
): UiPackageModel.SearchResult =
    UiPackageModel.SearchResult(
        packageModel = this,
        declaredScopes = declaredScopes,
        defaultScope = defaultScope,
        selectedVersion = searchResultUiState?.selectedVersion ?: getLatestAvailableVersion(onlyStable) ?: PackageVersion.Missing,
        selectedScope = searchResultUiState?.selectedScope ?: defaultScope,
        mixedBuildSystemTargets = mixedBuildSystems
    )

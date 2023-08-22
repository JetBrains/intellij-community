/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageOperationType
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.changePackage
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.installPackage
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.removePackage
import kotlinx.serialization.Serializable

@Serializable
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
    onlyStable: Boolean
): List<UiPackageModel.Installed> {
    val declaredScopes = declaredScopes(targetModules)
    val defaultScope = targetModules.defaultScope(project)
    return targetModules.modules.flatMap { usagesByModule[it] ?: emptyList() }
        .groupBy { it.scope }
        .map { (scope, usages) ->
            val availableVersions = getAvailableVersions(onlyStable)
            val hasUpgrade = usages.map { it.declaredVersion }.any { it < getHighestVersion(onlyStable) }
            UiPackageModel.Installed(
                packageModel = this,
                declaredScopes = declaredScopes,
                userDefinedScopes = userDefinedScopes(project),
                defaultVersion = availableVersions.firstOrNull() ?: latestInstalledVersion,
                defaultScope = defaultScope,
                selectedVersion = latestInstalledVersion,
                selectedScope = scope,
                mixedBuildSystemTargets = targetModules.isMixedBuildSystems,
                packageOperations = PackageOperations(
                    hasUpgrade = hasUpgrade,
                    primaryOperations = {
                        usages.forEach { usage ->
                            if (usage.declaredVersion < getHighestVersion(onlyStable)) {
                                changePackage(
                                    groupId = groupId,
                                    artifactId = artifactId,
                                    version = usage.declaredVersion.originalVersion,
                                    scope = usage.scope,
                                    newVersion = getHighestVersion(onlyStable).originalVersion,
                                    packageSearchModule = usage.module
                                )
                            }
                        }
                    },
                    removeOperations = {
                        usages.forEach { usage ->
                            removePackage(
                                groupId = groupId,
                                artifactId = artifactId,
                                version = usage.declaredVersion.originalVersion,
                                scope = usage.scope,
                                packageSearchModule = usage.module
                            )
                        }
                    },
                    targetVersion = getHighestVersion(onlyStable),
                    primaryOperationType = if (hasUpgrade) evaluatePackageOperationType(usages, onlyStable) else null,
                    repoToAddWhenInstalling = null
                ),
                sortedVersions = availableVersions
            )
        }
}

internal fun PackageModel.SearchResult.toUiPackageModel(
    onlyStable: Boolean,
    searchResultsUiStateOverrides: Map<PackageIdentifier, SearchResultUiState>,
    targetModules: TargetModules,
    project: Project,
    knownRepositoriesInTargetModules: Map<PackageSearchModule, List<RepositoryModel>>,
    allKnownRepositories: List<RepositoryModel>
): UiPackageModel.SearchResult {
    val sortedVersions = getAvailableVersions(onlyStable).sortedDescending()

    val selectedVersion = searchResultsUiStateOverrides[identifier]?.selectedVersion ?: sortedVersions.first()
    val selectedScope = searchResultsUiStateOverrides[identifier]?.selectedScope ?: targetModules.defaultScope(project)

    val repoToInstallByModule = repositoryToAddWhenInstallingOrUpgrading(
        targetModules = targetModules,
        knownRepositoriesInTargetModules = knownRepositoriesInTargetModules,
        allKnownRepositories = allKnownRepositories
    )
    return UiPackageModel.SearchResult(
        packageModel = this,
        declaredScopes = targetModules.declaredScopes(project),
        userDefinedScopes = emptyList(),
        defaultVersion = sortedVersions.first(),
        defaultScope = targetModules.defaultScope(project),
        selectedVersion = selectedVersion,
        selectedScope = selectedScope,
        mixedBuildSystemTargets = targetModules.isMixedBuildSystems,
        packageOperations = PackageOperations(
            primaryOperations = {
                targetModules.modules.forEach { module ->
                    installPackage(
                        groupId = groupId,
                        artifactId = artifactId,
                        version = selectedVersion.originalVersion,
                        scope = selectedScope.takeIf { it !is PackageScope.Missing }
                            ?: module.moduleType.defaultScope(project),
                        packageSearchModule = module
                    )
                    repoToInstallByModule[module]?.let { repoToInstall ->
                      installRepository(UnifiedDependencyRepository(repoToInstall.id, repoToInstall.name, repoToInstall.url), module)
                    }
                }
            },
            removeOperations = {},
            targetVersion = selectedVersion,
            primaryOperationType = PackageOperationType.INSTALL,
            repoToAddWhenInstalling = repoToInstallByModule.values.firstOrNull(),
            hasUpgrade = false
        ),
        sortedVersions = sortedVersions
    )
}

private fun PackageModel.Installed.evaluatePackageOperationType(usages: List<DependencyUsageInfo>, onlyStable: Boolean): PackageOperationType? {
    var isUpgrade = false
    for (usage in usages) {
        if (usage.declaredVersion is NormalizedPackageVersion.Missing) return PackageOperationType.SET
        if (!isUpgrade && usage.declaredVersion < getHighestVersion(onlyStable)) isUpgrade = true
    }
    return if (isUpgrade) PackageOperationType.UPGRADE else null
}

private fun PackageModel.Installed.declaredScopes(targetModules: TargetModules): List<PackageScope> =
    if (targetModules.modules.isNotEmpty()) {
        findUsagesIn(targetModules.modules).map { it.scope }
    } else {
        usagesByModule.values.flatten().map { it.scope }
    }
        .distinct()
        .sorted()

private fun PackageModel.Installed.userDefinedScopes(project: Project) =
    usagesByModule.values.asSequence()
        .flatten()
        .map { it.module }
        .distinct()
        .flatMap { it.moduleType.userDefinedScopes(project) }
        .toList()

internal fun PackageModel.repositoryToAddWhenInstallingOrUpgrading(
    targetModules: TargetModules,
    knownRepositoriesInTargetModules: Map<PackageSearchModule, List<RepositoryModel>>,
    allKnownRepositories: List<RepositoryModel>
): Map<PackageSearchModule, RepositoryModel> {
    val requiredRepoIds = remoteInfo?.latestVersion?.repositoryIds?.toSet() ?: return emptyMap()
    return targetModules.modules.mapNotNull {
        val knownRepoInModule = knownRepositoriesInTargetModules[it]
        when {
            knownRepoInModule.isNullOrEmpty() || knownRepoInModule.none { it.id in requiredRepoIds } ->
                allKnownRepositories.firstOrNull { it.id in requiredRepoIds }
            else -> null
        }?.let { repoToAdd -> it to repoToAdd }
    }.toMap()
}

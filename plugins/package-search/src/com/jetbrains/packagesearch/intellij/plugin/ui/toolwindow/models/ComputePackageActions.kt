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

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageOperationType
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.logWarn
import com.jetbrains.packagesearch.intellij.plugin.util.parallelForEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.jetbrains.packagesearch.packageversionutils.PackageVersionUtils

internal inline fun <reified T : PackageModel> CoroutineScope.computeActionsAsync(
    project: Project,
    packageModel: T,
    targetModules: TargetModules,
    knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
    onlyStable: Boolean,
    selectedScope: PackageScope? = null,
    selectedVersion: NormalizedPackageVersion<out PackageVersion>? = null
): PackageOperations {
    val operationFactory = PackageSearchOperationFactory()

    val availableVersions = packageModel.getAvailableVersions(onlyStable)

    val upgradeVersion = when {
        packageModel is PackageModel.Installed && availableVersions.isNotEmpty() -> {
            val currentVersion = packageModel.latestInstalledVersion
            PackageVersionUtils.upgradeCandidateVersionOrNull(currentVersion, availableVersions)
        }
        else -> null
    }

    val highestAvailableVersion = selectedVersion ?: availableVersions.takeIf { it.isNotEmpty() }
        ?.let { PackageVersionUtils.highestSensibleVersionByNameOrNull(availableVersions) }

    val primaryOperationType = decidePrimaryOperationTypeFor(packageModel, upgradeVersion)

    val primaryOperationsChannel = Channel<List<PackageSearchOperation<*>>>()
    val removeOperationsChannel = Channel<List<PackageSearchOperation<*>>>()

    val primaryOperations = async { primaryOperationsChannel.consumeAsFlow().flatMapConcat { it.asFlow() }.toList() }
    val removeOperations = async { removeOperationsChannel.consumeAsFlow().flatMapConcat { it.asFlow() }.toList() }
    launch {
        targetModules.modules.parallelForEach { moduleModel ->
            removeOperationsChannel.send(operationFactory.computeRemoveActionsFor(packageModel, moduleModel))

            val moduleProject = moduleModel.projectModule.nativeModule.project
            val operation = when (primaryOperationType) {
                PackageOperationType.INSTALL -> {
                    if (highestAvailableVersion != null) {
                        operationFactory.computeInstallActionsFor(
                            project = moduleProject,
                            packageModel = packageModel,
                            moduleModel = moduleModel,
                            defaultScope = selectedScope ?: targetModules.defaultScope(moduleProject),
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
                            project = moduleProject,
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
            primaryOperationsChannel.send(operation)
        }
        primaryOperationsChannel.close()
        removeOperationsChannel.close()
    }

    val repoToInstall = when (primaryOperationType) {
        PackageOperationType.INSTALL -> highestAvailableVersion?.let {
            knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(project, packageModel, it.originalVersion)
        }
        PackageOperationType.UPGRADE -> upgradeVersion?.let {
            knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(project, packageModel, it.originalVersion)
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
                packageModel.usageInfo.any { it.declaredVersion is PackageVersion.Missing } -> PackageOperationType.SET
                else -> PackageOperationType.UPGRADE
            }
        }
        else -> error("Unsupported package model type: ${packageModel::class.simpleName}")
    }

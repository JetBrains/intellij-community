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
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logError
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import org.jetbrains.packagesearch.packageversionutils.PackageVersionUtils
import kotlin.time.measureTime

internal suspend fun computePackageUpgrades(
    installedPackages: List<PackageModel.Installed>,
    onlyStable: Boolean,
    normalizer: PackageVersionNormalizer,
    repos: KnownRepositories.All,
    nativeModulesMap: Map<ProjectModule, ModuleModel>,
    trace: TraceInfo
): PackagesToUpgrade {
    logTrace(trace) { "Starting computation for ${installedPackages.size} installed packages" }
    val updatesByModule = mutableMapOf<Module, MutableSet<PackagesToUpgrade.PackageUpgradeInfo>>()
    val time = measureTime {
        val operationFactory = PackageSearchOperationFactory()
        for (installedPackageModel in installedPackages) {
            val availableVersions = installedPackageModel.getAvailableVersions(onlyStable)
            if (installedPackageModel.remoteInfo == null || availableVersions.isEmpty()) continue

            for (usageInfo in installedPackageModel.usageInfo) {
                val currentVersion = usageInfo.getDeclaredVersionOrFallback()
                if (currentVersion !is PackageVersion.Named) continue

                val normalizedPackageVersion = runCatching { normalizer.parse(currentVersion) }
                    .onFailure { logError(throwable = it) { "Unable to normalize version: $currentVersion" } }
                    .getOrNull() ?: continue
                if (updatesByModule.size % 100 == 0 && updatesByModule.isNotEmpty()) logTrace(trace) { "updatesByModule.size = ${updatesByModule.size}" }
                val upgradeVersion = PackageVersionUtils.upgradeCandidateVersionOrNull(normalizedPackageVersion, availableVersions)
                val moduleModel = nativeModulesMap[usageInfo.projectModule]
                if (upgradeVersion != null && upgradeVersion.originalVersion is PackageVersion.Named && moduleModel != null) {
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
    }
    logTrace(trace) { "Finished in $time" }
    return PackagesToUpgrade(updatesByModule)
}

private fun computeUpgradeOperationsForSingleModule(
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

package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.asUnifiedDependencyKeyOrNull
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.*
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.DependencyUsageInfo
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InstalledDependenciesUsages
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InstalledDependency
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ProjectDataProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.packageVersionNormalizer
import kotlinx.coroutines.future.await

internal suspend fun installedDependenciesUsages(
  project: Project,
  packageSearchModules: Map<PackageSearchModule, PackageSearchModule.Dependencies>,
  remoteData: Map<InstalledDependency, ProjectDataProvider.ParsedApiStandardPackage>
): InstalledDependenciesUsages {
    val installedVersions: MutableMap<InstalledDependency, MutableList<NormalizedPackageVersion<*>>> = mutableMapOf()
    val usages = buildMap<InstalledDependency, MutableList<DependencyUsageInfo>> {
        packageSearchModules.forEach { (module, dependencies) ->
            val resolvedDependencies = buildMap {
                dependencies.resolved.forEach { unifiedDependency ->
                    val key = unifiedDependency.asUnifiedDependencyKeyOrNull()
                    if (key != null) put(key, unifiedDependency)
                }
            }

            dependencies.declared
                .groupBy { it.asInstalledDependency() }
                .forEach { (installedDependencyKey, declaredDependencies) ->
                    declaredDependencies.map { declaredDependency ->
                        val usage = DependencyUsageInfo(
                          module = module,
                          declaredVersion = packageVersionNormalizer.parse(PackageVersion.from(declaredDependency.coordinates.version)),
                          resolvedVersion = declaredDependency.unifiedDependency.asUnifiedDependencyKeyOrNull()
                            ?.let { unifiedDependencyKey -> resolvedDependencies[unifiedDependencyKey] }
                            ?.coordinates
                            ?.version
                            .let { packageVersionNormalizer.parse(PackageVersion.Companion.from(it)) },
                          scope = PackageScope.from(declaredDependency.unifiedDependency.scope),
                          userDefinedScopes = module.moduleType.userDefinedScopes(project),
                          declarationIndexInBuildFile = module.dependencyDeclarationCallback(declaredDependency).await()
                        )
                        getOrPut(installedDependencyKey) { mutableListOf() }.add(usage)
                        installedVersions.getOrPut(installedDependencyKey) { mutableListOf() }.add(usage.declaredVersion)
                    }
                }
        }
    }
    val installedPackagesByModule = mutableMapOf<Module, MutableList<PackageModel.Installed>>()
    val allInstalledPackages = usages.asSequence()
        .map { (installedDependency, usages) ->
            val parsedRemoteVersions = (remoteData[installedDependency]?.parsedVersions ?: emptyList())
                .sortedDescending()
            val allVersions = installedVersions.getValue(installedDependency)
                .plus(remoteData[installedDependency]?.parsedVersions ?: emptyList())
                .sortedDescending()
          PackageModel.Installed(
            groupId = installedDependency.groupId,
            artifactId = installedDependency.artifactId,
            remoteInfo = remoteData[installedDependency]?.data,
            remoteVersions = parsedRemoteVersions,
            usagesByModule = usages.groupBy { it.module.nativeModule },
            latestInstalledVersion = installedVersions.getValue(installedDependency).max(),
            highestStableVersion = allVersions.firstOrNull { it.isStable } ?: allVersions.first(),
            highestUnstableVersion = allVersions.first(),
            declaredVersions = installedVersions.getValue(installedDependency)
          )
        }
        .onEach { packageModel ->
            packageModel.usagesByModule.forEach { (_, usages) ->
                usages.forEach { usage ->
                    installedPackagesByModule.getOrPut(usage.module.nativeModule) { mutableListOf() }
                        .add(packageModel)
                }
            }
        }
        .toList()
    return InstalledDependenciesUsages(allInstalledPackages, installedPackagesByModule)
}
package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.DependencyUsageInfo
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InstalledDependency
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ProjectDataProvider
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.parallelMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal suspend fun installedPackages(
    dependenciesByModule: Map<ProjectModule, List<UnifiedDependency>>,
    project: Project,
    dataProvider: ProjectDataProvider,
    traceInfo: TraceInfo
): List<PackageModel.Installed> {
    val usageInfoByDependency = mutableMapOf<UnifiedDependency, MutableList<DependencyUsageInfo>>()
    for (module in dependenciesByModule.keys) {
        dependenciesByModule[module]?.forEach { dependency ->
            // Skip packages we don't know the version for
            val rawVersion = dependency.coordinates.version

            val usageInfo = DependencyUsageInfo(
                projectModule = module,
                version = PackageVersion.from(rawVersion),
                scope = PackageScope.from(dependency.scope),
                userDefinedScopes = module.moduleType.userDefinedScopes(project)
                    .map { rawScope -> PackageScope.from(rawScope) }
            )
            val usageInfoList = usageInfoByDependency.getOrPut(dependency) { mutableListOf() }
            usageInfoList.add(usageInfo)
        }
    }

    val installedDependencies = dependenciesByModule.values.flatten()
        .mapNotNull { InstalledDependency.from(it) }

    val dependencyRemoteInfoMap = dataProvider.fetchInfoFor(installedDependencies, traceInfo)

    return usageInfoByDependency.parallelMap { (dependency, usageInfo) ->
        val installedDependency = InstalledDependency.from(dependency)
        val remoteInfo = if (installedDependency != null) {
            dependencyRemoteInfoMap[installedDependency]
        } else {
            null
        }

        PackageModel.fromInstalledDependency(
            unifiedDependency = dependency,
            usageInfo = usageInfo,
            remoteInfo = remoteInfo
        )
    }.filterNotNull().sortedBy { it.sortKey }
}

internal suspend fun List<ProjectModule>.fetchProjectDependencies(): Map<ProjectModule, List<UnifiedDependency>> =
    coroutineScope {
        associateWith { module -> async { module.installedDependencies() } }
            .mapValues { (_, value) -> value.await() }
    }

internal suspend fun ProjectModule.installedDependencies() =
    readAction { ProjectModuleOperationProvider.forProjectModuleType(moduleType)?.listDependenciesInModule(this@installedDependencies) }
        ?.toList()
        ?: emptyList()

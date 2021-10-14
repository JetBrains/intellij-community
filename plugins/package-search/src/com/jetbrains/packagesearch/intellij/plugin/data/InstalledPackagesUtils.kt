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
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.parallelMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal suspend fun installedPackages(
    projectModules: List<ProjectModule>,
    project: Project,
    dataProvider: ProjectDataProvider,
    traceInfo: TraceInfo
): List<PackageModel.Installed> {
    val dependenciesByModule = fetchProjectDependencies(projectModules, traceInfo)
    val usageInfoByDependency = mutableMapOf<UnifiedDependency, MutableList<DependencyUsageInfo>>()

    for (module in projectModules) {
        for (dependency in dependenciesByModule[module] ?: emptyList()) {
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

private suspend fun fetchProjectDependencies(modules: List<ProjectModule>, traceInfo: TraceInfo): Map<ProjectModule, List<UnifiedDependency>> =
    coroutineScope {
        modules.associateWith { module -> async { module.installedDependencies(traceInfo) } }
            .mapValues { (_, value) -> value.await() }
    }

private suspend fun ProjectModule.installedDependencies(traceInfo: TraceInfo): List<UnifiedDependency> {
    logDebug(traceInfo, "installedDependencies()") { "Fetching installed dependencies for module $name..." }
    return readAction { ProjectModuleOperationProvider.forProjectModuleType(moduleType) }
        ?.let { provider -> readAction { provider.listDependenciesInModule(this@installedDependencies) } }
        ?.toList() ?: emptyList()
}

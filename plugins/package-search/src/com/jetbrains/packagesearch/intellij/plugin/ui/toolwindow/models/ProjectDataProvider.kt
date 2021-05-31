package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import com.jetbrains.packagesearch.api.v2.ApiRepository
import com.jetbrains.packagesearch.api.v2.ApiStandardPackage
import com.jetbrains.packagesearch.intellij.plugin.api.PackageSearchApiClient
import com.jetbrains.packagesearch.intellij.plugin.api.http.ApiResult
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.packagesearch.intellij.plugin.util.logWarn
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.apache.commons.collections.map.LRUMap

internal class ProjectDataProvider(
    private val apiClient: PackageSearchApiClient
) {

    private val packagesCache = LRUMap(500)

    suspend fun fetchKnownRepositories(): ApiResult<List<ApiRepository>> = apiClient.repositories()
        .mapSuccess { it.repositories }

    suspend fun doSearch(searchQuery: String, filterOptions: FilterOptions): ApiResult<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>> {
        val repositoryIds = filterOptions.onlyRepositoryIds

        return apiClient.packagesByQuery(
            searchQuery = searchQuery,
            onlyStable = filterOptions.onlyStable,
            onlyMpp = filterOptions.onlyKotlinMultiplatform,
            repositoryIds = repositoryIds.toList()
        )
    }

    suspend fun fetchInfoFor(installedDependencies: List<InstalledDependency>, traceInfo: TraceInfo): Map<InstalledDependency, ApiStandardPackage> {
        if (installedDependencies.isEmpty()) {
            return emptyMap()
        }

        val apiInfoByDependency = fetchInfoFromCacheOrApiFor(installedDependencies, traceInfo)

        val filteredApiInfo = apiInfoByDependency.filterValues { it == null }
        if (filteredApiInfo.isNotEmpty() && filteredApiInfo.size != installedDependencies.size) {
            val failedDependencies = filteredApiInfo.keys

            logWarn(traceInfo, "ProjectDataProvider#fetchInfoFor()") {
                "Failed obtaining data for ${failedDependencies.size} dependencies:\n" +
                    failedDependencies.joinToString("\n") { "\t* '${it.coordinatesString}'" }
            }
        }

        @Suppress("UNCHECKED_CAST") // We filter out null values before casting, we should be ok
        return apiInfoByDependency.filterValues { it != null } as Map<InstalledDependency, ApiStandardPackage>
    }

    private suspend fun fetchInfoFromCacheOrApiFor(
        dependencies: List<InstalledDependency>,
        traceInfo: TraceInfo
    ): Map<InstalledDependency, ApiStandardPackage?> {
        logDebug(traceInfo, "ProjectDataProvider#fetchInfoFromCacheOrApiFor()") {
            "Fetching data for ${dependencies.count()} dependencies..."
        }

        val dependenciesMap = mutableMapOf<InstalledDependency, ApiStandardPackage?>()
        val packagesToFetch = mutableListOf<InstalledDependency>()
        for (dependency in dependencies) {
            val standardV2Package = packagesCache[dependency]
            dependenciesMap[dependency] = standardV2Package as ApiStandardPackage?
            if (standardV2Package == null) {
                packagesToFetch += dependency
            }
        }

        if (packagesToFetch.isEmpty()) {
            logTrace(traceInfo, "ProjectDataProvider#fetchInfoFromCacheOrApiFor()") {
                "Found all ${dependencies.count() - packagesToFetch.count()} packages in cache"
            }
            return dependenciesMap
        }

        logTrace(traceInfo, "ProjectDataProvider#fetchInfoFromCacheOrApiFor()") {
            "Found ${dependencies.count() - packagesToFetch.count()} packages in cache, still need to fetch ${packagesToFetch.count()} from API"
        }

        val fetchedPackages = packagesToFetch.asSequence()
            .map { dependency -> dependency.coordinatesString }
            .chunked(size = 25)
            .asFlow()
            .map { dependenciesToFetch -> apiClient.packagesByRange(dependenciesToFetch) }
            .filterIsInstance<ApiResult.Success<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>>>()
            .map { it.result.packages }
            .toList()
            .flatten()

        for (v2Package in fetchedPackages) {
            val dependency = InstalledDependency.from(v2Package)
            packagesCache[dependency] = v2Package
            dependenciesMap[dependency] = v2Package
        }

        return dependenciesMap
    }
}

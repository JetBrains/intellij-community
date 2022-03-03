package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import com.jetbrains.packagesearch.api.v2.ApiRepository
import com.jetbrains.packagesearch.api.v2.ApiStandardPackage
import com.jetbrains.packagesearch.intellij.plugin.api.PackageSearchApiClient
import com.jetbrains.packagesearch.intellij.plugin.util.CoroutineLRUCache
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.logInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

internal class ProjectDataProvider(
    private val apiClient: PackageSearchApiClient,
    private val packageCache: CoroutineLRUCache<InstalledDependency, ApiStandardPackage>
) {

    suspend fun fetchKnownRepositories(): List<ApiRepository> = apiClient.repositories().repositories

    suspend fun doSearch(
        searchQuery: String,
        filterOptions: FilterOptions
    ): ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion> {
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

        val (emptyApiInfoByDependency, successfulApiInfoByDependency) =
            apiInfoByDependency.partition { (_, v) -> v == null }

        if (emptyApiInfoByDependency.isNotEmpty() && emptyApiInfoByDependency.size != installedDependencies.size) {
            val failedDependencies = emptyApiInfoByDependency.keys

            logInfo(traceInfo, "ProjectDataProvider#fetchInfoFor()") {
                "Failed obtaining data for ${failedDependencies.size} dependencies"
            }
        }

        return successfulApiInfoByDependency.filterNotNullValues()
    }

    private suspend fun fetchInfoFromCacheOrApiFor(
        dependencies: List<InstalledDependency>,
        traceInfo: TraceInfo
    ): Map<InstalledDependency, ApiStandardPackage?> {
        logDebug(traceInfo, "ProjectDataProvider#fetchInfoFromCacheOrApiFor()") {
            "Fetching data for ${dependencies.count()} dependencies..."
        }

        val remoteInfoByDependencyMap = mutableMapOf<InstalledDependency, ApiStandardPackage?>()
        val packagesToFetch = mutableListOf<InstalledDependency>()
        for (dependency in dependencies) {
            val standardV2Package = packageCache.get(dependency)
            remoteInfoByDependencyMap[dependency] = standardV2Package
            if (standardV2Package == null) {
                packagesToFetch += dependency
            }
        }

        if (packagesToFetch.isEmpty()) {
            logTrace(traceInfo, "ProjectDataProvider#fetchInfoFromCacheOrApiFor()") {
                "Found all ${dependencies.count() - packagesToFetch.count()} packages in cache"
            }
            return remoteInfoByDependencyMap
        }

        logTrace(traceInfo, "ProjectDataProvider#fetchInfoFromCacheOrApiFor()") {
            "Found ${dependencies.count() - packagesToFetch.count()} packages in cache, still need to fetch ${packagesToFetch.count()} from API"
        }

        val fetchedPackages = packagesToFetch.asSequence()
            .map { dependency -> dependency.coordinatesString }
            .chunked(size = 25)
            .asFlow()
            .map { dependenciesToFetch -> apiClient.packagesByRange(dependenciesToFetch) }
            .map { it.packages }
            .catch {
                logDebug(
                    "${this::class.run { qualifiedName ?: simpleName ?: this }}#fetchedPackages",
                    it,
                ) { "Error while retrieving packages" }
                emit(emptyList())
            }
            .toList()
            .flatten()

        for (v2Package in fetchedPackages) {
            val dependency = InstalledDependency.from(v2Package)
            packageCache.put(dependency, v2Package)
            remoteInfoByDependencyMap[dependency] = v2Package
        }

        return remoteInfoByDependencyMap
    }
}

private fun <K, V> Map<K, V>.partition(transform: (Map.Entry<K, V>) -> Boolean): Pair<Map<K, V>, Map<K, V>> {
    val trueMap = mutableMapOf<K, V>()
    val falseMap = mutableMapOf<K, V>()
    forEach { if (transform(it)) trueMap[it.key] = it.value else falseMap[it.key] = it.value }
    return trueMap to falseMap
}

private fun <K, V> Map<K, V?>.filterNotNullValues() = buildMap<K, V> {
    this@filterNotNullValues.forEach { (k, v) -> if (v != null) put(k, v) }
}

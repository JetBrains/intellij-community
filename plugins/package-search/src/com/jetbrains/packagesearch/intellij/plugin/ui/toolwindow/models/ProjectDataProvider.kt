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

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.CoroutineLRUCache
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.logInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.packagesearch.intellij.plugin.util.packageVersionNormalizer
import com.jetbrains.packagesearch.intellij.plugin.util.parallelMap
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.idea.packagesearch.api.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import org.jetbrains.packagesearch.api.v2.ApiRepository
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage
import java.io.Closeable

typealias SearchResponse = ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>

internal class ProjectDataProvider(
    private val apiClient: PackageSearchApiClient,
    private val packageCache: CoroutineLRUCache<InstalledDependency, ApiStandardPackage>
) : Closeable by apiClient {

    suspend fun fetchKnownRepositories(): List<ApiRepository> = apiClient.repositories().repositories

    suspend fun doSearch(
        searchQuery: String,
        filterOptions: FilterOptions
    ): ParsedSearchResponse {
        val repositoryIds = filterOptions.onlyRepositoryIds

        val packagesByQuery = apiClient.packagesByQuery(
            searchQuery = searchQuery,
            onlyStable = filterOptions.onlyStable,
            onlyMpp = filterOptions.onlyKotlinMultiplatform,
            repositoryIds = repositoryIds.toList()
        )

        return ParsedSearchResponse(
            data = packagesByQuery,
            parsedVersions = packagesByQuery.packages.associateWith {
                it.versions
                    .map { PackageVersion.from(it) }
                    .filterIsInstance<PackageVersion.Named>()
                    .parallelMap { packageVersionNormalizer.parse(it) }
            }
        )
    }

    data class ParsedApiStandardPackage(val data: ApiStandardPackage, val parsedVersions: List<NormalizedPackageVersion<PackageVersion.Named>>)
    data class ParsedSearchResponse(
        val data: SearchResponse,
        val parsedVersions: Map<ApiStandardPackage, List<NormalizedPackageVersion<PackageVersion.Named>>>
    )

    suspend fun fetchInfoFor(
        installedDependencies: Collection<InstalledDependency>,
        traceInfo: TraceInfo
    ): Map<InstalledDependency, ParsedApiStandardPackage> {
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

        return successfulApiInfoByDependency.filterNotNullValues().mapValues { (_, data) ->
            ParsedApiStandardPackage(
                data = data,
                parsedVersions = data.versions
                    .map { PackageVersion.from(it) }
                    .filterIsInstance<PackageVersion.Named>()
                    .map { packageVersionNormalizer.parse(it) }
            )
        }
    }

    private suspend fun fetchInfoFromCacheOrApiFor(
        dependencies: Collection<InstalledDependency>,
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

        packagesToFetch.asSequence()
            .map { dependency -> dependency.coordinatesString }
            .distinct()
            .sorted()
            .also {
                logTrace(traceInfo, "ProjectDataProvider#fetchInfoFromCacheOrApiFor()") {
                    "Found ${dependencies.count() - packagesToFetch.count()} packages in cache, still need to fetch ${it.count()} from API"
                }
            }
            .chunked(size = 25)
            .asFlow()
            .buffer(25)
            .map { dependenciesToFetch -> apiClient.packagesByRange(dependenciesToFetch).packages }
            .catch {
                logDebug(
                    "${this::class.run { qualifiedName ?: simpleName ?: this }}#fetchedPackages",
                    it,
                ) { "Error while retrieving packages" }
                emit(emptyList())
            }
            .toList()
            .flatten()
            .forEach { v2Package ->
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

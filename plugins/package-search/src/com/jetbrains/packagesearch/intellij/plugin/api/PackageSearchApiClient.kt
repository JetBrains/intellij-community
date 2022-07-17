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

package com.jetbrains.packagesearch.intellij.plugin.api

import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.api.http.requestString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.commons.httpclient.util.URIUtil
import org.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import org.jetbrains.packagesearch.api.v2.ApiRepositoriesResponse
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage

internal object ServerURLs {

    const val base = "https://package-search.services.jetbrains.com/api"
}

private val contentType by lazy {
    @Suppress("MayBeConst") // False positive
    object {
        val standard = "application/vnd.jetbrains.packagesearch.standard.v2+json"
    }
}

private val emptyStandardV2PackagesWithRepos = ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>(
    packages = emptyList(),
    repositories = emptyList()
)

internal class PackageSearchApiClient(
    private val baseUrl: String,
    private val timeoutInSeconds: Int = 10,
    private val headers: List<Pair<String, String>> = listOf(
        Pair("JB-Plugin-Version", PluginEnvironment.pluginVersion),
        Pair("JB-IDE-Version", PluginEnvironment.ideVersion)
    )
) {

    private val maxRequestResultsCount = 25
    private val maxMavenCoordinatesParts = 3

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    suspend fun packagesByQuery(
        searchQuery: String,
        onlyStable: Boolean = false,
        onlyMpp: Boolean = false,
        repositoryIds: List<String>
    ): ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion> {
        if (searchQuery.isEmpty()) {
            return emptyStandardV2PackagesWithRepos
        }

        val joinedRepositoryIds = repositoryIds.joinToString(",") { URIUtil.encodeQuery(it) }
        val requestUrl = buildString {
            append(baseUrl)
            append("/package?query=")
            append(URIUtil.encodeQuery(searchQuery))
            append("&onlyStable=")
            append(onlyStable.toString())
            append("&onlyMpp=")
            append(onlyMpp.toString())

            if (repositoryIds.isNotEmpty()) {
                append("&repositoryIds=")
                append(joinedRepositoryIds)
            }
        }

        return requestString(requestUrl, contentType.standard, timeoutInSeconds, headers)
            .let { json.decodeFromString(it) }
    }

    suspend fun packagesByRange(range: List<String>): ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion> {
        if (range.isEmpty()) {
            return emptyStandardV2PackagesWithRepos
        }

        require(range.size <= maxRequestResultsCount) {
            PackageSearchBundle.message("packagesearch.search.client.error.too.many.requests.for.range")
        }

        require(range.none { it.split(":").size >= maxMavenCoordinatesParts }) {
            PackageSearchBundle.message("packagesearch.search.client.error.no.versions.for.range")
        }

        val joinedRange = range.joinToString(",") { URIUtil.encodeQuery(it) }
        val requestUrl = "$baseUrl/package?range=$joinedRange"

        return requestString(requestUrl, contentType.standard, timeoutInSeconds, headers)
            .let { json.decodeFromString(it) }
    }

    suspend fun repositories(): ApiRepositoriesResponse {
        return requestString("$baseUrl/repositories", contentType.standard, timeoutInSeconds, headers)
            .let { json.decodeFromString(it) }
    }
}

/*
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
 */

package org.jetbrains.idea.packagesearch.api

import com.intellij.openapi.components.service
import com.intellij.util.io.URLUtil
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.idea.packagesearch.DefaultPackageServiceConfig
import org.jetbrains.idea.packagesearch.HashingAlgorithm
import org.jetbrains.idea.packagesearch.PackageSearchServiceConfig
import org.jetbrains.idea.packagesearch.http.HttpWrapper
import org.jetbrains.idea.reposearch.DependencySearchBundle
import org.jetbrains.packagesearch.api.statistics.ApiStatisticsResponse
import org.jetbrains.packagesearch.api.v2.ApiPackageResponse
import org.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import org.jetbrains.packagesearch.api.v2.ApiRepositoriesResponse
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage


internal object PackageSearchApiContentTypes {
  const val StandardV2 = "application/vnd.jetbrains.packagesearch.standard.v2+json"
  const val MinimalV2 = "application/vnd.jetbrains.packagesearch.minimal.v2+json"
  const val Json = "application/json"
  const val Html = "text/html"
}

private val emptyStandardV2PackagesWithRepos = ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>(
  packages = emptyList(),
  repositories = emptyList()
)

class PackageSearchApiClient(
  private val config: PackageSearchServiceConfig = service<DefaultPackageServiceConfig>()
) {
  private val httpWrapper = HttpWrapper()

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
    repositoryIds: List<String> = emptyList()
  ): ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion> {
    if (searchQuery.isEmpty()) {
      return emptyStandardV2PackagesWithRepos
    }

    val joinedRepositoryIds = repositoryIds.joinToString(",") { URLUtil.encodeQuery(it) }
    val requestUrl = buildString {
      append(config.baseUrl)
      append("/package?query=")
      append(URLUtil.encodeQuery(searchQuery))
      append("&onlyStable=")
      append(onlyStable.toString())
      append("&onlyMpp=")
      append(onlyMpp.toString())

      if (repositoryIds.isNotEmpty()) {
        append("&repositoryIds=")
        append(joinedRepositoryIds)
      }
    }

    return httpWrapper.requestString(
      requestUrl,
      PackageSearchApiContentTypes.StandardV2,
      config.timeoutInSeconds,
      config.headers,
      config.useCache
    ).let { json.decodeFromString(it) }
  }

  suspend fun suggestPackages(
    groupId: String?,
    artifactId: String?,
    onlyMpp: Boolean = false,
    repositoryIds: List<String> = emptyList()
  ): ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion> {
    if (groupId == null && artifactId == null) {
      return emptyStandardV2PackagesWithRepos
    }

    val joinedRepositoryIds = repositoryIds.joinToString(",") { URLUtil.encodeQuery(it) }
    val requestUrl = buildString {
      append(config.baseUrl)
      append("/package?groupid=")
      append(URLUtil.encodeQuery(groupId ?: ""))
      append("&artifactid=")
      append(URLUtil.encodeQuery(artifactId ?: ""))
      append("&onlyMpp=")
      append(onlyMpp.toString())

      if (repositoryIds.isNotEmpty()) {
        append("&repositoryIds=")
        append(joinedRepositoryIds)
      }
    }

    return httpWrapper.requestString(
      requestUrl,
      PackageSearchApiContentTypes.StandardV2,
      config.timeoutInSeconds,
      config.headers,
      config.useCache
    ).let { json.decodeFromString(it) }
  }

  suspend fun packagesByRange(range: List<String>): ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion> {
    if (range.isEmpty()) {
      return emptyStandardV2PackagesWithRepos
    }

    require(range.size <= maxRequestResultsCount) {
      DependencySearchBundle.message("reposearch.search.client.error.too.many.requests.for.range")
    }

    require(range.none { it.split(":").size >= maxMavenCoordinatesParts }) {
      DependencySearchBundle.message("reposearch.search.client.error.no.versions.for.range")
    }

    val joinedRange = range.joinToString(",") { URLUtil.encodeQuery(it) }
    val requestUrl = "${config.baseUrl}/package?range=$joinedRange"

    return httpWrapper.requestString(
      requestUrl,
      PackageSearchApiContentTypes.StandardV2,
      config.timeoutInSeconds,
      config.headers,
      config.useCache
    ).let { json.decodeFromString(it) }
  }

  suspend fun packageByHash(
    hash: String,
    hashingAlgorithm: HashingAlgorithm
  ): ApiPackageResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion> {
    val urlParam = when (hashingAlgorithm) {
      HashingAlgorithm.SHA1 -> "sha1=$hash"
      HashingAlgorithm.MD5 -> "md5=$hash"
    }

    return httpWrapper.requestString(
      "${config.baseUrl}/package?$urlParam",
      PackageSearchApiContentTypes.StandardV2,
      config.timeoutInSeconds,
      config.headers,
      config.useCache
    ).let { json.decodeFromString(it) }
  }

  suspend fun packageById(id: String): ApiPackageResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion> {
    return httpWrapper.requestString(
      "${config.baseUrl}/package/$id",
      PackageSearchApiContentTypes.StandardV2,
      config.timeoutInSeconds,
      config.headers,
      config.useCache
    ).let { json.decodeFromString(it) }
  }

  suspend fun readmeByPackageId(id: String): String {
    return httpWrapper.requestString(
      "${config.baseUrl}/package/$id/readme",
      PackageSearchApiContentTypes.Html,
      config.timeoutInSeconds,
      config.headers,
      config.useCache
    )
  }

  suspend fun statistics(): ApiStatisticsResponse {
    return httpWrapper.requestString(
      "${config.baseUrl}/api/statistics",
      PackageSearchApiContentTypes.Json,
      config.timeoutInSeconds,
      config.headers,
      config.useCache
    ).let { json.decodeFromString(it) }
  }

  suspend fun repositories(): ApiRepositoriesResponse {
    return httpWrapper.requestString(
      "${config.baseUrl}/repositories",
      PackageSearchApiContentTypes.StandardV2,
      config.timeoutInSeconds,
      config.headers,
      config.useCache
    ).let { json.decodeFromString(it) }
  }
}
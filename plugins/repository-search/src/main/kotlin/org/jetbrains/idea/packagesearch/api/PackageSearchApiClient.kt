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
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.appendEncodedPathSegments
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.jetbrains.idea.packagesearch.DefaultPackageServiceConfig
import org.jetbrains.idea.packagesearch.HashingAlgorithm
import org.jetbrains.idea.packagesearch.PackageSearchServiceConfig
import org.jetbrains.idea.reposearch.DependencySearchBundle
import org.jetbrains.packagesearch.api.statistics.ApiStatisticsResponse
import org.jetbrains.packagesearch.api.v2.ApiPackageResponse
import org.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import org.jetbrains.packagesearch.api.v2.ApiRepositoriesResponse
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage
import java.io.Closeable


class PackageSearchApiClient(
  private val config: PackageSearchServiceConfig = service<DefaultPackageServiceConfig>(),
  engine: HttpClientEngine? = null,
) : Closeable {

  private val clientConfig: HttpClientConfig<*>.() -> Unit
    get() = {
      install(ContentNegotiation) {
        val json = Json {
          ignoreUnknownKeys = true
          encodeDefaults = false
        }
        packageSearch(json)
        json(json)
      }
      install(Logging) {
        logger = config.logger
        level = config.logLevel
      }
      install(UserAgent) {
        agent = config.userAgent
      }
      install(DefaultRequest) {
        url {
          protocol = config.protocol
          host = config.host
          path("api/")
        }
      }
      install(HttpTimeout) {
        requestTimeout = config.timeout
      }
      install(HttpRequestRetry) {
        retryOnServerErrors(5)
        constantDelay()
      }
      if (config.useCache) install(HttpCache)
    }

  private val httpClient = if (engine != null) HttpClient(engine, clientConfig) else HttpClient(CIO, clientConfig)

  private val maxRequestResultsCount = 25
  private val maxMavenCoordinatesParts = 3

  suspend fun packagesByQuery(
    searchQuery: String,
    onlyStable: Boolean = false,
    onlyMpp: Boolean = false,
    repositoryIds: List<String> = emptyList()
  ): ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion> {
    if (searchQuery.isEmpty()) {
      return emptyStandardV2PackagesWithRepos
    }

    return httpClient.getBody {
      url {
        appendEncodedPathSegments("package")
        parameters {
          append("query", searchQuery)
          append("onlyStable", onlyStable)
          append("onlyMpp", onlyMpp)
          if (repositoryIds.isNotEmpty()) {
            append("repositoryIds", repositoryIds)
          }
        }
      }
    }
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

    return httpClient.getBody {
      url {
        appendEncodedPathSegments("packages")
        parameters {
          append("groupid", groupId ?: "")
          append("artifactid", artifactId ?: "")
          append("onlyMpp", onlyMpp)
          if (repositoryIds.isNotEmpty()) {
            append("repositoryIds", repositoryIds)
          }
        }
      }
    }
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

    return httpClient.getBody {
      url {
        appendEncodedPathSegments("package")
        parameters {
          append("range", range)
        }
      }
    }
  }

  suspend fun packageByHash(
    hash: String,
    hashingAlgorithm: HashingAlgorithm
  ): ApiPackageResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion> = httpClient.getBody {
    url {
      appendEncodedPathSegments("package")
      parameters {
        append(hashingAlgorithm.name.lowercase(), hash)
      }
    }
  }

  suspend fun packageById(id: String): ApiPackageResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion> =
    httpClient.getBody {
      url {
        appendEncodedPathSegments("package", id)
      }
    }

  suspend fun readmeByPackageId(id: String): String =
    httpClient.getBody {
      url {
        appendEncodedPathSegments("package", id, "readme")
      }
      accept(ContentType.Text.Html)
    }

  suspend fun statistics(): ApiStatisticsResponse =
    httpClient.getBody {
      url {
        appendEncodedPathSegments("statistics")
      }
    }

  suspend fun repositories(): ApiRepositoriesResponse =
    httpClient.getBody {
      url {
        appendEncodedPathSegments("repositories")
      }
    }

  override fun close() = httpClient.close()
}


package com.jetbrains.packagesearch.intellij.plugin.api

import com.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import com.jetbrains.packagesearch.api.v2.ApiRepositoriesResponse
import com.jetbrains.packagesearch.api.v2.ApiStandardPackage
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.api.http.ApiResult
import com.jetbrains.packagesearch.intellij.plugin.api.http.requestString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.commons.httpclient.util.URIUtil

internal object ServerURLs {

    const val base = "https://package-search.services.jetbrains.com/api"
}

private val pluginEnvironment = PluginEnvironment()

@Suppress("unused") // Used in SearchClient but the lazy throws off the IDE code analysis
private val contentType by lazy {
    @Suppress("MayBeConst") // False positive
    object {
        val standard = "application/vnd.jetbrains.packagesearch.standard.v2+json"
    }
}

private val emptyStandardV2PackagesWithRepos: ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion> = ApiPackagesResponse(
    packages = emptyList(),
    repositories = emptyList()
)

internal class PackageSearchApiClient(
    private val baseUrl: String,
    private val timeoutInSeconds: Int = 10,
    private val headers: List<Pair<String, String>> = listOf(
        Pair("JB-Plugin-Version", pluginEnvironment.pluginVersion),
        Pair("JB-IDE-Version", pluginEnvironment.ideVersion)
    )
) {

    private val maxRequestResultsCount = 25
    private val maxMavenCoordinatesParts = 3

    private val serializer = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun packagesByQuery(
        searchQuery: String,
        onlyStable: Boolean = false,
        onlyMpp: Boolean = false,
        repositoryIds: List<String>
    ): ApiResult<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>> {
        if (searchQuery.isEmpty()) {
            return ApiResult.Success(emptyStandardV2PackagesWithRepos)
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
            .mapSuccess { serializer.decodeFromString(it) }
    }

    suspend fun packagesByRange(range: List<String>): ApiResult<ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>> {
        if (range.isEmpty()) {
            return ApiResult.Success(emptyStandardV2PackagesWithRepos)
        }
        if (range.size > maxRequestResultsCount) {
            return argumentError(PackageSearchBundle.message("packagesearch.search.client.error.too.many.requests.for.range"))
        }
        if (range.any { it.split(":").size >= maxMavenCoordinatesParts }) {
            return argumentError(PackageSearchBundle.message("packagesearch.search.client.error.no.versions.for.range"))
        }

        val joinedRange = range.joinToString(",") { URIUtil.encodeQuery(it) }
        val requestUrl = "$baseUrl/package?range=$joinedRange"

        return requestString(requestUrl, contentType.standard, timeoutInSeconds, headers)
            .mapSuccess { serializer.decodeFromString(it) }
    }

    private fun <T : Any> argumentError(message: String) = ApiResult.Failure<T>(IllegalArgumentException(message))

    suspend fun repositories(): ApiResult<ApiRepositoriesResponse> =
        requestString("$baseUrl/repositories", contentType.standard, timeoutInSeconds, headers)
            .mapSuccess { serializer.decodeFromString(it) }
}

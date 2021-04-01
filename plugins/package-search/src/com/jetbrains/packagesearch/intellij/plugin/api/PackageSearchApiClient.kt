package com.jetbrains.packagesearch.intellij.plugin.api

import com.google.gson.Gson
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.api.http.ApiResult
import com.jetbrains.packagesearch.intellij.plugin.api.http.requestJsonObject
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2PackagesWithRepos
import com.jetbrains.packagesearch.intellij.plugin.api.model.V2Repositories
import com.jetbrains.packagesearch.intellij.plugin.gson.EnumWithDeserializationFallbackAdapterFactory
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

private val emptyStandardV2PackagesWithRepos = StandardV2PackagesWithRepos(
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

    private val gson = Gson().newBuilder()
        // https://youtrack.jetbrains.com/issue/PKGS-547
        // Ensures enum values in our model are not null if a default value is available
        // (works around cases like https://discuss.kotlinlang.org/t/json-enum-deserialization-breakes-kotlin-null-safety/11670)
        .registerTypeAdapterFactory(EnumWithDeserializationFallbackAdapterFactory())
        .create()

    fun packagesByQuery(
        searchQuery: String,
        onlyStable: Boolean = false,
        onlyMpp: Boolean = false,
        repositoryIds: List<String>
    ): ApiResult<StandardV2PackagesWithRepos> {
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

        return requestJsonObject(requestUrl, contentType.standard, timeoutInSeconds, headers)
            .mapSuccess { gson.fromJson(it, StandardV2PackagesWithRepos::class.java) }
    }

    fun packagesByRange(range: List<String>): ApiResult<StandardV2PackagesWithRepos> {
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

        return requestJsonObject(requestUrl, contentType.standard, timeoutInSeconds, headers)
            .mapSuccess { gson.fromJson(it, StandardV2PackagesWithRepos::class.java) }
    }

    private fun <T : Any> argumentError(message: String) = ApiResult.Failure<T>(IllegalArgumentException(message))

    fun repositories(): ApiResult<V2Repositories> =
        requestJsonObject("$baseUrl/repositories", contentType.standard, timeoutInSeconds, headers)
            .mapSuccess { gson.fromJson(it, V2Repositories::class.java) }
}

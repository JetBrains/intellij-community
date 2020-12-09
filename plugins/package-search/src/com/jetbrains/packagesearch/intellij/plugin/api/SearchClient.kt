package com.jetbrains.packagesearch.intellij.plugin.api

import arrow.core.Either
import com.google.gson.Gson
import com.jetbrains.packagesearch.intellij.plugin.FeatureFlags
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.api.http.requestJsonObject
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2PackagesWithRepos
import com.jetbrains.packagesearch.intellij.plugin.api.model.V2Repositories
import com.jetbrains.packagesearch.intellij.plugin.api.model.V2Repository
import com.jetbrains.packagesearch.intellij.plugin.api.model.V2RepositoryType
import com.jetbrains.packagesearch.intellij.plugin.api.query.language.PackageSearchQuery
import com.jetbrains.packagesearch.intellij.plugin.gson.EnumWithDeserializationFallbackAdapterFactory
import org.apache.commons.httpclient.util.URIUtil

object ServerURLs {
    const val base = "https://package-search.services.jetbrains.com/api"
}

private val pluginEnvironment by lazy { PluginEnvironment() }

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

class SearchClient(
    private val baseUrl: String,
    private val timeoutInSeconds: Int = 10,
    private val headers: List<Pair<String, String>> = listOf(
        Pair("JB-Plugin-Version", pluginEnvironment.pluginVersion),
        Pair("JB-IDE-Version", pluginEnvironment.ideVersion)
    )
) {

    private val maxRequestResultsCount = 25
    private val maxMavenCoordinatesParts = 3

    private val mockRepositories = V2Repositories(listOf(
        V2Repository("maven_central", "https://repo1.maven.org/maven2/",
            V2RepositoryType.MAVEN, listOf("https://repo.maven.apache.org/maven2/"), "Maven Central"),
        V2Repository("gmaven", "https://maven.google.com/",
            V2RepositoryType.MAVEN, emptyList(), "Google Maven"),
        V2Repository("jcenter", "https://jcenter.bintray.com/",
            V2RepositoryType.MAVEN, emptyList(), "JCenter")
    ))

    private val gson = Gson().newBuilder()
        // https://youtrack.jetbrains.com/issue/PKGS-547
        // Ensures enum values in our model are not null if a default value is available
        // (works around cases like https://discuss.kotlinlang.org/t/json-enum-deserialization-breakes-kotlin-null-safety/11670)
        .registerTypeAdapterFactory(EnumWithDeserializationFallbackAdapterFactory())
        .create()

    fun packagesByQuery(query: PackageSearchQuery, onlyStable: Boolean = false, onlyMpp: Boolean = false, repositoryIds: List<String>):
        Either<String, StandardV2PackagesWithRepos> {

        val searchQuery = query.searchQuery ?: return Either.right(emptyStandardV2PackagesWithRepos)

        if (searchQuery.isEmpty()) {
            return Either.right(emptyStandardV2PackagesWithRepos)
        }

        val joinedRepositoryIds = repositoryIds.joinToString(",") { URIUtil.encodeQuery(it) }
        val requestUrl = "$baseUrl/package?query=${URIUtil.encodeQuery(searchQuery)}" +
            "&onlyStable=$onlyStable" +
            "&onlyMpp=$onlyMpp" +
            "&repositoryIds=$joinedRepositoryIds"

        return requestJsonObject(requestUrl, contentType.standard, timeoutInSeconds, headers)
            .fold(
                { Either.left(it) },
                { Either.right(gson.fromJson(it, StandardV2PackagesWithRepos::class.java).injectMockRepositories(repositoryIds)) })
    }

    fun packagesByRange(range: List<String>): Either<String, StandardV2PackagesWithRepos> {
        if (range.isEmpty()) {
            return Either.right(emptyStandardV2PackagesWithRepos)
        }
        if (range.size > maxRequestResultsCount) {
            return Either.left(PackageSearchBundle.message("packagesearch.search.client.error.too.many.requests.for.range"))
        }
        if (range.any { it.split(":").size >= maxMavenCoordinatesParts }) {
            return Either.left(PackageSearchBundle.message("packagesearch.search.client.error.no.versions.for.range"))
        }

        val joinedRange = range.joinToString(",") { URIUtil.encodeQuery(it) }
        val requestUrl = "$baseUrl/package?range=$joinedRange"

        return requestJsonObject(requestUrl, contentType.standard, timeoutInSeconds, headers)
            .fold(
                { Either.left(it) },
                { Either.right(gson.fromJson(it, StandardV2PackagesWithRepos::class.java).injectMockRepositories()) })
    }

    private fun StandardV2PackagesWithRepos.injectMockRepositories(repositoryIds: List<String>? = null): StandardV2PackagesWithRepos {

        if (!FeatureFlags.mockRepositoriesApi) return this

        val packages = this.packages
            ?.map { standardV2Package: StandardV2Package ->

                val index = standardV2Package.toSimpleIdentifier().length

                val repositoryId = mockRepositories.repositories
                    ?.get(index.rem(mockRepositories.repositories.size))?.id
                    ?: "maven_central"

                val latestVersion = standardV2Package.latestVersion
                    .copy(repositoryIds = listOf(repositoryId))

                val versions = standardV2Package.versions
                    ?.map { it.copy(repositoryIds = listOf(repositoryId)) }
                    ?: emptyList()

                val name: String? = standardV2Package.name

                standardV2Package.copy(
                    name = name ?: "",
                    latestVersion = latestVersion,
                    versions = versions)
            }?.filter { repositoryIds.isNullOrEmpty() ||
                it.latestVersion.repositoryIds.isNullOrEmpty() ||
                it.latestVersion.repositoryIds.any { repo -> repositoryIds.contains(repo) } }

        return StandardV2PackagesWithRepos(packages, mockRepositories.repositories)
    }

    fun repositories(): Either<String, V2Repositories> {

        if (FeatureFlags.mockRepositoriesApi) {
            return Either.right(mockRepositories)
        }

        val requestUrl = "$baseUrl/repositories"

        return requestJsonObject(requestUrl, contentType.standard, timeoutInSeconds, headers)
            .fold(
                { Either.left(it) },
                { Either.right(gson.fromJson(it, V2Repositories::class.java)) })
    }
}

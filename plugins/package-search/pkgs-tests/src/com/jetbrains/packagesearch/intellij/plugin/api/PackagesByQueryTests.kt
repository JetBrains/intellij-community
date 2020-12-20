package com.jetbrains.packagesearch.intellij.plugin.api

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNotZero
import assertk.assertions.prop
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2GitHub
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2PackagesWithRepos
import com.jetbrains.packagesearch.intellij.plugin.api.query.language.PackageSearchQuery
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test

class PackagesByQueryTests : SearchClientTestsBase() {

    @Test
    fun `should return empty list on empty search by query`() {
        val search = createSearchClient()
        val results = search.packagesByQuery(
            query(""), onlyStable = false, onlyMpp = false, repositoryIds = emptyList()
        )

        assertThat(results).isRight()
            .rightValue()
            .isNotNull()

        assertThat(results).isRight()
            .rightValue()
            .prop(StandardV2PackagesWithRepos::packages)
            .isNotNull()
            .isEmpty()
    }

    @Test
    fun `should return list of matching dependencies on non-empty search by query`() {
        val search = createSearchClient()
        val results = search.packagesByQuery(
            query("okhttp"), onlyStable = false, onlyMpp = false, repositoryIds = emptyList()
        )

        assertThat(results).isRight()
            .rightValue()
            .prop(StandardV2PackagesWithRepos::packages)
            .isNotNull()
            .hasSize(25)
    }

    @Test
    fun `should fail with unknown URL message when cannot resolve server`() {
        val search = createSearchClient("https://nonexistingdomainsomethingelse.com")
        val results = search.packagesByQuery(
            query("okhttp"), onlyStable = false, onlyMpp = false, repositoryIds = emptyList()
        )

        assertThat(results).isLeft()
            .leftValue()
            .contains("nonexistingdomainsomethingelse.com")
    }

    @Test
    fun `should fail with timeout message when cannot reach server`() {
        val server = MockWebServer()
        val response = MockResponse().setBody("this response times out")
        response.setBodyDelay(3, TimeUnit.SECONDS)
        server.enqueue(response)
        server.start()
        val search = createSearchClient(server.url("/noendpoint").toString(), timeout = 1)
        val results = search.packagesByQuery(
            query("okhttp"), onlyStable = false, onlyMpp = false, repositoryIds = emptyList()
        )

        assertThat(results).isLeft()
            .leftValue()
            .isEqualTo("Read timed out")
    }

    @Test
    fun `should return valid dependency data on valid search`() {
        val search = createSearchClient()
        val results = search.packagesByQuery(
            query("com.squareup.okhttp:okhttp"), onlyStable = false, onlyMpp = false, repositoryIds = emptyList()
        )

        assertThat(results).isRight()
            .rightValue()
            .prop(StandardV2PackagesWithRepos::packages)
            .isNotNull().given { dependencies ->
                val okHttp3Dependency = dependencies.first { it.groupId == "com.squareup.okhttp" && it.artifactId == "okhttp" }

                assertThat(okHttp3Dependency).all {
                    prop(StandardV2Package::gitHub).isNotNull().all {
                        prop(StandardV2GitHub::forks).isNotZero()
                        prop(StandardV2GitHub::stars).isNotZero()
                    }
                    prop(StandardV2Package::latestVersion).isNotNull()
                        .transform { it.version }
                        .isNotEmpty()
                }
            }
    }

    private fun query(searchQuery: String): PackageSearchQuery = PackageSearchQuery(searchQuery)
        .apply {
            this.searchQuery = searchQuery
        }
}

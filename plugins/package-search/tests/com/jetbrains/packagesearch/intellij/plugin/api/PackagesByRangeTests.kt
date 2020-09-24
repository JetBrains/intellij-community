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
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test

class PackagesByRangeTests : SearchClientTestsBase() {

    @Test
    fun `should return empty list on empty search by range`() {
        val search = createSearchClient()
        val results = search.packagesByRange(emptyList())

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
    fun `should return list of matching dependencies on non-empty search by range`() {
        val search = createSearchClient()
        val results = search.packagesByRange(
            listOf(
                "org.junit.jupiter:junit-jupiter",
                "com.willowtreeapps.assertk:assertk-jvm",
                "com.squareup.okhttp3:mockwebserver"
            )
        )

        assertThat(results).isRight()
            .rightValue()
            .prop(StandardV2PackagesWithRepos::packages)
            .isNotNull()
            .hasSize(3)
    }

    @Test
    fun `should fail with unknown URL message when cannot resolve server`() {
        val search = createSearchClient("https://noexistingdomainsomethingelse.com")
        val results = search.packagesByRange(
            listOf(
                "org.junit.jupiter:junit-jupiter"
            )
        )

        assertThat(results).isLeft()
            .leftValue()
            .contains("noexistingdomainsomethingelse.com")
    }

    @Test
    fun `should fail with timeout message when cannot reach server`() {
        val server = MockWebServer()
        val response = MockResponse().setBody("this response times out")
        response.setBodyDelay(3, TimeUnit.SECONDS)
        server.enqueue(response)
        server.start()
        val search = createSearchClient(server.url("/noendpoint").toString(), timeout = 1)
        val results = search.packagesByRange(
            listOf(
                "org.junit.jupiter:junit-jupiter"
            )
        )

        assertThat(results).isLeft()
            .leftValue()
            .isEqualTo("Read timed out")
    }

    @Test
    fun `should return valid dependency data on valid search`() {
        val search = createSearchClient()
        val results = search.packagesByRange(
            listOf(
                "org.junit.jupiter:junit-jupiter",
                "com.willowtreeapps.assertk:assertk-jvm",
                "com.squareup.okhttp3:mockwebserver"
            )
        )

        assertThat(results).isRight()
            .rightValue()
            .prop(StandardV2PackagesWithRepos::packages)
            .isNotNull()
            .transform { it.first() }
            .all {
                prop(StandardV2Package::gitHub).isNotNull().all {
                    prop(StandardV2GitHub::forks).isNotZero()
                    prop(StandardV2GitHub::stars).isNotZero()
                }
                prop(StandardV2Package::latestVersion).isNotNull()
                    .transform { it.version }
                    .isNotEmpty()
            }
    }

    @Test
    fun `should fail with larger than allowed range`() {
        val search = createSearchClient()
        val results = search.packagesByRange(
            listOf(
                "01", "02", "03", "04", "05", "06", "07", "08", "09", "10",
                "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
                "21", "22", "23", "24", "25", "26"
            )
        )

        assertThat(results).isLeft()
            .leftValue()
            .contains("larger than maximum")
    }

    @Test
    fun `should fail with version numbers specified`() {
        val search = createSearchClient()
        val results = search.packagesByRange(
            listOf(
                "foo:bar:1.2.3"
            )
        )

        assertThat(results).isLeft()
            .leftValue()
            .contains("should not contain version numbers")
    }
}

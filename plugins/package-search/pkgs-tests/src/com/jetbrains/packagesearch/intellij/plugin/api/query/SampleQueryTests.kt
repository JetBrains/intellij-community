package com.jetbrains.packagesearch.intellij.plugin.api.query

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNullOrEmpty
import com.jetbrains.packagesearch.intellij.plugin.api.query.language.SampleQuery
import org.junit.jupiter.api.Test

class SampleQueryTests {

    @Test
    fun `can parse empty query`() {
        val searchQuery = SampleQuery("")

        assertThat(searchQuery.searchQuery).isNullOrEmpty()
        assertThat(searchQuery.buildQueryString()).isEqualTo("q=&onlyStable=true&onlyMpp=false")
    }

    @Test
    fun `can parse query without attributes`() {
        val searchQuery = SampleQuery("ktor test")

        assertThat(searchQuery.searchQuery).isEqualTo("ktor test")
        assertThat(searchQuery.buildQueryString()).isEqualTo("q=ktor%20test&onlyStable=true&onlyMpp=false")
    }

    @Test
    fun `can parse query with attributes`() {
        val searchQuery = SampleQuery("ktor /onlyStable:false /tag:test")

        assertThat(searchQuery.searchQuery).isEqualTo("ktor")
        assertThat(searchQuery.buildQueryString()).isEqualTo("q=ktor&onlyStable=false&onlyMpp=false&tags=test")
    }
}

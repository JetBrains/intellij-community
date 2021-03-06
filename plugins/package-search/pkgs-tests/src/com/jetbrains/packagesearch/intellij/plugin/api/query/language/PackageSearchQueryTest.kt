package com.jetbrains.packagesearch.intellij.plugin.api.query.language

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jetbrains.packagesearch.intellij.plugin.api.query.SampleQueryTests
import org.junit.jupiter.api.Test

/** @see SampleQueryTests for more examples */
internal class PackageSearchQueryTest {

    @Test
    fun `should parse an empty query as null`() {
        val searchQuery = PackageSearchQuery("")

        assertThat(searchQuery.searchQuery).isNull()
    }

    @Test
    fun `should parse a query without attributes correctly`() {
        val searchQuery = PackageSearchQuery("ktor test")

        assertThat(searchQuery.searchQuery).isEqualTo("ktor test")
    }
}

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions

import com.jetbrains.packagesearch.intellij.plugin.assertThat
import com.jetbrains.packagesearch.intellij.plugin.isEqualTo
import com.jetbrains.packagesearch.intellij.plugin.isNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource

internal class VeryLenientDateTimeExtractorTest {

    @ParameterizedTest
    @CsvFileSource(resources = ["/invalid-date-time-prefixes.csv"])
    internal fun `should return null when there is no datetime-like prefix`(versionName: String) {
        val looksLikeATimestamp = VeryLenientDateTimeExtractor.extractTimestampLookingPrefixOrNull(versionName)
        assertThat(looksLikeATimestamp).isNull()
    }

    @ParameterizedTest
    @CsvFileSource(resources = ["/valid-date-time-prefixes.csv"])
    internal fun `should return the prefix when there is a datetime-like prefix`(versionName: String, expected: String) {
        assertThat(VeryLenientDateTimeExtractor.extractTimestampLookingPrefixOrNull(versionName)).isEqualTo(expected)
    }
}

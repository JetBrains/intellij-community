package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions

import com.jetbrains.packagesearch.intellij.plugin.asNullable
import com.jetbrains.packagesearch.intellij.plugin.assertThat
import com.jetbrains.packagesearch.intellij.plugin.isEqualTo
import com.jetbrains.packagesearch.intellij.plugin.isInstanceOf
import com.jetbrains.packagesearch.intellij.plugin.isNotInstanceOf
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.aNamedPackageVersion
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource

internal class PackageVersionNormalizerTest {

    private val normalizer = PackageVersionNormalizer()

    @Nested
    inner class Semantic {

        @Test
        internal fun `should return a Semantic version when the input is a simple semantic version`() {
            val aVersion = aNamedPackageVersion(versionName = "1.0.0")
            assertThat(normalizer.parseBlocking(aVersion)).isInstanceOf(NormalizedPackageVersion.Semantic::class)
                .isEqualTo(
                    NormalizedPackageVersion.Semantic(
                        original = aVersion,
                        semanticPart = "1.0.0",
                        stabilityMarker = null,
                        nonSemanticSuffix = null
                    )
                )
        }

        @Test
        internal fun `should return a Semantic version when the input is a semantic version with stability marker`() {
            val aVersion = aNamedPackageVersion(versionName = "1.0.0-RC02")
            assertThat(normalizer.parseBlocking(aVersion)).isInstanceOf(NormalizedPackageVersion.Semantic::class)
                .isEqualTo(
                    NormalizedPackageVersion.Semantic(
                        original = aVersion,
                        semanticPart = "1.0.0",
                        stabilityMarker = "-RC02",
                        nonSemanticSuffix = null
                    )
                )
        }

        @Test
        internal fun `should return a Semantic version when the input is a semantic version with non semantic suffix`() {
            val aVersion = aNamedPackageVersion(versionName = "1.0.0-banana2")
            assertThat(normalizer.parseBlocking(aVersion)).isInstanceOf(NormalizedPackageVersion.Semantic::class)
                .isEqualTo(
                    NormalizedPackageVersion.Semantic(
                        original = aVersion,
                        semanticPart = "1.0.0",
                        stabilityMarker = null,
                        nonSemanticSuffix = "-banana2"
                    )
                )
        }

        @Test
        internal fun `should return a Semantic version when the input is a semantic version with stability marker and non semantic suffix`() {
            val aVersion = aNamedPackageVersion(versionName = "1.0.0-b03-banana2")
            assertThat(normalizer.parseBlocking(aVersion)).isInstanceOf(NormalizedPackageVersion.Semantic::class)
                .isEqualTo(
                    NormalizedPackageVersion.Semantic(
                        original = aVersion,
                        semanticPart = "1.0.0",
                        stabilityMarker = "-b03",
                        nonSemanticSuffix = "-banana2"
                    )
                )
        }

        @ParameterizedTest(name = "[{index}] {0} -> semanticPart={1}, stabilityMarker={2}, suffix={3}")
        @CsvFileSource(resources = ["/valid-semantic-data.csv"])
        internal fun `should return a valid Semantic version for all valid cases`(
            versionName: String,
            semanticPart: String,
            stabilityMarker: String?,
            nonSemanticSuffix: String?
        ) {
            val aVersion = aNamedPackageVersion(versionName = versionName)
            assertThat(normalizer.parseBlocking(aVersion)).isInstanceOf(NormalizedPackageVersion.Semantic::class)
                .isEqualTo(NormalizedPackageVersion.Semantic(aVersion, semanticPart, stabilityMarker.asNullable(), nonSemanticSuffix.asNullable()))
        }

        @Test
        internal fun `should not return a Semantic version when the input has too many parts`() {
            val aVersion = aNamedPackageVersion(versionName = "1.0.0.0.0.1")
            assertThat(normalizer.parseBlocking(aVersion)).isNotInstanceOf(NormalizedPackageVersion.Semantic::class)
        }

        @Test
        internal fun `should not return a Semantic version when the input only has one big number`() {
            val aVersion = aNamedPackageVersion(versionName = "100000")
            assertThat(normalizer.parseBlocking(aVersion)).isNotInstanceOf(NormalizedPackageVersion.Semantic::class)
        }

        @Test
        internal fun `should not return a Semantic version when the input looks like a date`() {
            val aVersion = aNamedPackageVersion(versionName = "20201005")
            assertThat(normalizer.parseBlocking(aVersion)).isNotInstanceOf(NormalizedPackageVersion.Semantic::class)
        }

        @Test
        internal fun `should not return a Semantic version when the input looks like a datetime`() {
            val aVersion = aNamedPackageVersion(versionName = "20201005120015")
            assertThat(normalizer.parseBlocking(aVersion)).isNotInstanceOf(NormalizedPackageVersion.Semantic::class)
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @CsvFileSource(resources = ["/invalid-semantic-data.csv"])
        internal fun `should not return a Semantic version when the input is not a sensible semver`(versionName: String) {
            val aVersion = aNamedPackageVersion(versionName)
            assertThat(normalizer.parseBlocking(aVersion)).isNotInstanceOf(NormalizedPackageVersion.Semantic::class)
        }
    }

    @Nested
    inner class Timestamp {

        @ParameterizedTest(name = "[{index}] {0} -> timestampPrefix={1}, stabilityMarker={2}, suffix={3}")
        @CsvFileSource(resources = ["/valid-timestamp-data.csv"])
        internal fun `should return a valid TimestampLike version for all valid cases`(
            versionName: String,
            timestampPrefix: String,
            stabilityMarker: String,
            nonSemanticSuffix: String?
        ) {
            val aVersion = aNamedPackageVersion(versionName = versionName)
            assertThat(normalizer.parseBlocking(aVersion)).isInstanceOf(NormalizedPackageVersion.TimestampLike::class)
                .isEqualTo(NormalizedPackageVersion.TimestampLike(
                    aVersion,
                    timestampPrefix,
                    stabilityMarker.asNullable(),
                    nonSemanticSuffix.asNullable()
                ))
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @CsvFileSource(resources = ["/invalid-timestamp-data.csv"])
        internal fun `should not return a TimestampLike version when the input is not a valid TimestampLike version`(versionName: String) {
            val aVersion = aNamedPackageVersion(versionName)
            assertThat(normalizer.parseBlocking(aVersion)).isNotInstanceOf(NormalizedPackageVersion.TimestampLike::class)
        }
    }
}

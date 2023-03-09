/*******************************************************************************
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
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions

import com.jetbrains.packagesearch.intellij.plugin.PackageSearchTestUtils
import com.jetbrains.packagesearch.intellij.plugin.asNullable
import com.jetbrains.packagesearch.intellij.plugin.assertThat
import com.jetbrains.packagesearch.intellij.plugin.isEqualTo
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.aGarbageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.aNamedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.aSemanticVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.aTimestampLikeVersion
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import kotlin.math.sign

internal class NormalizedPackageVersionTest {

    @Nested
    inner class DifferentTypeOrdering {

        @Test
        internal fun `should return 1 when comparing a Semantic version to a TimestampLike version`() {
            assertThat(aSemanticVersion().compareTo(aTimestampLikeVersion())).isEqualTo(1)
        }

        @Test
        internal fun `should return 1 when comparing a Semantic version to a Garbage version`() {
            assertThat(aSemanticVersion().compareTo(aGarbageVersion())).isEqualTo(1)
        }

        @Test
        internal fun `should return 1 when comparing a TimestampLike version to a Garbage version`() {
            assertThat(aTimestampLikeVersion().compareTo(aGarbageVersion())).isEqualTo(1)
        }

        @Test
        internal fun `should return -1 when comparing a TimestampLike version to a Semantic version`() {
            assertThat(aTimestampLikeVersion().compareTo(aSemanticVersion())).isEqualTo(-1)
        }

        @Test
        internal fun `should return -1 when comparing a Garbage version to a Semantic version`() {
            assertThat(aGarbageVersion().compareTo(aSemanticVersion())).isEqualTo(-1)
        }

        @Test
        internal fun `should return -1 when comparing a Garbage version to a TimestampLike version`() {
            assertThat(aGarbageVersion().compareTo(aTimestampLikeVersion())).isEqualTo(-1)
        }
    }

    @Nested
    inner class SameTypeOrdering {

        @ParameterizedTest(name = "[{index}] \"{0}\".comparedTo(\"{4}\") = {8}")
        @CsvFileSource(resources = ["/semantic-comparison-data.csv"])
        internal fun `should compare semantic versions correctly`(
            version1Name: String,
            version1SemanticPart: String,
            version1Stability: String,
            version1ReleasedAt: String,
            version2Name: String,
            version2SemanticPart: String,
            version2Stability: String,
            version2ReleasedAt: String,
            expected: Int
        ) {
            val version1 = NormalizedPackageVersion.Semantic(
                aNamedPackageVersion(version1Name, releasedAt = version1ReleasedAt.asNullable()?.toLong()),
                semanticPart = version1SemanticPart,
                stabilityMarker = version1Stability,
                nonSemanticSuffix = null
            )
            val version2 = NormalizedPackageVersion.Semantic(
                aNamedPackageVersion(version2Name, releasedAt = version2ReleasedAt.asNullable()?.toLong()),
                semanticPart = version2SemanticPart,
                stabilityMarker = version2Stability,
                nonSemanticSuffix = null
            )
            assertThat(version1.compareTo(version2).sign).isEqualTo(expected)
        }

        @ParameterizedTest(name = "[{index}] \"{0}\".comparedTo(\"{4}\") = {8}")
        @CsvFileSource(resources = ["/timestamp-comparison-data.csv"])
        internal fun `should compare timestamp versions correctly`(
            version1Name: String,
            version1TimestampPrefix: String,
            version1Stability: String,
            version1ReleasedAt: String,
            version2Name: String,
            version2TimestampPrefix: String,
            version2Stability: String,
            version2ReleasedAt: String,
            expected: Int
        ) {
            val version1 = NormalizedPackageVersion.TimestampLike(
                aNamedPackageVersion(version1Name, releasedAt = version1ReleasedAt.asNullable()?.toLong()),
                timestampPrefix = version1TimestampPrefix,
                stabilityMarker = version1Stability,
                nonSemanticSuffix = null
            )
            val version2 = NormalizedPackageVersion.TimestampLike(
                aNamedPackageVersion(version2Name, releasedAt = version2ReleasedAt.asNullable()?.toLong()),
                timestampPrefix = version2TimestampPrefix,
                stabilityMarker = version2Stability,
                nonSemanticSuffix = null
            )
            assertThat(version1.compareTo(version2).sign).isEqualTo(expected)
        }

        @ParameterizedTest(name = "[{index}] \"{0}\".comparedTo(\"{2}\") = {4}")
        @CsvFileSource(resources = ["/garbage-comparison-data.csv"])
        internal fun `should compare garbage versions correctly`(
            version1Name: String,
            version1ReleasedAt: String,
            version2Name: String,
            version2ReleasedAt: String,
            expected: Int
        ) {
            val version1 = NormalizedPackageVersion.Garbage(
                aNamedPackageVersion(version1Name, releasedAt = version1ReleasedAt.asNullable()?.toLong()),
            )
            val version2 = NormalizedPackageVersion.Garbage(
                aNamedPackageVersion(version2Name, releasedAt = version2ReleasedAt.asNullable()?.toLong()),
            )
            assertThat(version1.compareTo(version2).sign).isEqualTo(expected)
        }
    }

    @Nested
    inner class RealWorldExamples {

        @Test
        internal fun `should sort apache commons-io versions correctly`() = runTest {
            val expectedVersions = loadFromRes("versions_list/commons-io.csv")
                .map { PackageVersionNormalizer().parse(it) }

            assertThat(expectedVersions.shuffled().sortedDescending()).isEqualTo(expectedVersions)
        }

        @Test
        internal fun `should sort scala-compiler versions correctly`() = runTest {
            val expectedVersions = loadFromRes("versions_list/scala-compiler.csv")
                .map { PackageVersionNormalizer().parse(it) }

            assertThat(expectedVersions.shuffled().sortedDescending()).isEqualTo(expectedVersions)
        }

        @Test
        internal fun `should sort postgresql versions correctly`() = runTest {
            val expectedVersions = loadFromRes("versions_list/postgresql.csv")
                .map { PackageVersionNormalizer().parse(it) }

            assertThat(expectedVersions.shuffled().sortedDescending()).isEqualTo(expectedVersions)
        }

        @Test
        internal fun `should sort compose-full versions correctly`() = runTest {
            val expectedVersions = loadFromRes("versions_list/compose-full.csv")
                .map { PackageVersionNormalizer().parse(it) }

            assertThat(expectedVersions.shuffled().sortedDescending()).isEqualTo(expectedVersions)
        }

        private fun loadFromRes(path: String) =
            PackageSearchTestUtils.loadPackageVersionsFromResource(path)
    }
}


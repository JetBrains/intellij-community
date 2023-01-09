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

import com.intellij.util.text.VersionComparatorUtil
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.versionTokenPriorityProvider
import kotlinx.serialization.Serializable

@Serializable
sealed class NormalizedPackageVersion<T : PackageVersion> : Comparable<NormalizedPackageVersion<*>> {

    abstract val originalVersion: T

    companion object

    val versionName: String
        get() = originalVersion.versionName

    val displayName: String
        get() = originalVersion.displayName

    val isStable: Boolean
        get() = originalVersion.isStable

    val releasedAt: Long?
        get() = originalVersion.releasedAt

    @Serializable
    data class Semantic(
        override val originalVersion: PackageVersion.Named,
        val semanticPart: String,
        override val stabilityMarker: String?,
        override val nonSemanticSuffix: String?
    ) : NormalizedPackageVersion<PackageVersion.Named>(), DecoratedVersion {

        val semanticPartWithStabilityMarker
            get() = semanticPart + (stabilityMarker ?: "")

        override fun compareTo(other: NormalizedPackageVersion<*>): Int =
            when (other) {
                is Semantic -> compareByNameAndThenByTimestamp(other)
                is TimestampLike, is Garbage, is Missing -> 1
                else -> 0
            }

        private fun compareByNameAndThenByTimestamp(other: Semantic): Int {
            // First, compare semantic parts and stability markers only
            val nameComparisonResult = VersionComparatorUtil.compare(
                semanticPartWithStabilityMarker,
                other.semanticPartWithStabilityMarker,
                ::versionTokenPriorityProvider
            )
            if (nameComparisonResult != 0) return nameComparisonResult

            // If they're identical, but only one has a non-semantic suffix, that's the larger one.
            // If both or neither have a non-semantic suffix, we move to the next step
            when {
                nonSemanticSuffix.isNullOrBlank() && !other.nonSemanticSuffix.isNullOrBlank() -> return -1
                !nonSemanticSuffix.isNullOrBlank() && other.nonSemanticSuffix.isNullOrBlank() -> return 1
            }

            // If both have a comparable non-semantic suffix, and they're different, that determines the result.
            // Blank/null suffixes aren't comparable, so if they're both null/blank, we move to the next step
            if (canBeUsedForComparison(nonSemanticSuffix) && canBeUsedForComparison(other.nonSemanticSuffix)) {
                val comparisonResult = VersionComparatorUtil.compare(versionName, other.versionName, ::versionTokenPriorityProvider)
                if (comparisonResult != 0) return comparisonResult
            }

            // Fallback: neither has a comparable non-semantic suffix, we can consider it the same
            return 0
        }

        private fun canBeUsedForComparison(nonSemanticSuffix: String?): Boolean {
            if (nonSemanticSuffix.isNullOrBlank()) return false
            val normalizedSuffix = nonSemanticSuffix.trim().lowercase()
            val hasGitHashLength = normalizedSuffix.length in 7..10 || normalizedSuffix.length == 40
            if (hasGitHashLength && normalizedSuffix.all { it.isDigit() || it in HEX_CHARS || !it.isLetter() }) return false
            return true
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Semantic

            return compareTo(other) == 0
        }

        companion object {

            private val HEX_CHARS = 'a'..'f'
        }
    }

    @Serializable
    data class TimestampLike(
        override val originalVersion: PackageVersion.Named,
        val timestampPrefix: String,
        override val stabilityMarker: String?,
        override val nonSemanticSuffix: String?
    ) : NormalizedPackageVersion<PackageVersion.Named>(), DecoratedVersion {

        private val timestampPrefixWithStabilityMarker
            get() = timestampPrefix + (stabilityMarker ?: "")

        override fun compareTo(other: NormalizedPackageVersion<*>): Int =
            when (other) {
                is TimestampLike -> compareByNameAndThenByTimestamp(other)
                is Semantic -> -1
                is Garbage, is Missing -> 1
                else -> 0
            }



        private fun compareByNameAndThenByTimestamp(other: TimestampLike): Int {
            val nameComparisonResult = VersionComparatorUtil.compare(
                timestampPrefixWithStabilityMarker,
                other.timestampPrefixWithStabilityMarker,
                ::versionTokenPriorityProvider
            )

            return if (nameComparisonResult == 0) {
                originalVersion.compareByTimestamp(other.originalVersion)
            } else {
                nameComparisonResult
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TimestampLike

            return compareTo(other) == 0
        }
    }

    @Serializable
    data class Garbage(override val originalVersion: PackageVersion.Named) : NormalizedPackageVersion<PackageVersion.Named>() {

        override fun compareTo(other: NormalizedPackageVersion<*>): Int =
            when (other) {
                is Missing -> 1
                is Garbage -> compareByNameAndThenByTimestamp(other)
                is Semantic, is TimestampLike -> -1
                else -> 0
            }

        private fun compareByNameAndThenByTimestamp(other: Garbage): Int {
            val nameComparisonResult = VersionComparatorUtil.compare(originalVersion.versionName, other.originalVersion.versionName)
            return if (nameComparisonResult == 0) {
                originalVersion.compareByTimestamp(other.originalVersion)
            } else {
                nameComparisonResult
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Garbage

            return compareTo(other) == 0
        }

    }

    @Serializable
    object Missing : NormalizedPackageVersion<PackageVersion.Missing>() {

        override val originalVersion: PackageVersion.Missing
            get() = PackageVersion.Missing

        override fun compareTo(other: NormalizedPackageVersion<*>): Int =
            when (other) {
                is Missing -> 0
                else -> -1
            }
    }

    // If only one of them has a releasedAt, it wins. If neither does, they're equal.
    // If both have a releasedAt, we use those to discriminate.
    protected fun PackageVersion.Named.compareByTimestamp(other: PackageVersion.Named) =
        when {
            releasedAt == null && other.releasedAt != null -> -1
            releasedAt != null && other.releasedAt == null -> 1
            releasedAt != null && other.releasedAt != null -> releasedAt.compareTo(other.releasedAt)
            else -> 0
        }

    fun nonSemanticSuffixOrNull(): String? =
        when (this) {
            is Semantic -> nonSemanticSuffix
            is TimestampLike -> nonSemanticSuffix
            is Garbage, is Missing -> null
            else -> null
        }

    interface DecoratedVersion {

        val stabilityMarker: String?

        val nonSemanticSuffix: String?
    }
}

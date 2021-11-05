package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions

import com.intellij.util.text.VersionComparatorUtil
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.versionTokenPriorityProvider
import kotlinx.serialization.Serializable

@Serializable
internal sealed class NormalizedPackageVersion<T : PackageVersion>(
    val originalVersion: T
) : Comparable<NormalizedPackageVersion<*>> {

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
        private val original: PackageVersion.Named,
        val semanticPart: String,
        override val stabilityMarker: String?,
        override val nonSemanticSuffix: String?
    ) : NormalizedPackageVersion<PackageVersion.Named>(original), DecoratedVersion {

        val semanticPartWithStabilityMarker = semanticPart + (stabilityMarker ?: "")

        override fun compareTo(other: NormalizedPackageVersion<*>): Int =
            when (other) {
                is Semantic -> compareByNameAndThenByTimestamp(other)
                is TimestampLike, is Garbage, is Missing -> 1
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

            // Fallback: neither has a comparable non-semantic suffix, so timestamp is all we're left with
            return original.compareByTimestamp(other.original)
        }

        private fun canBeUsedForComparison(nonSemanticSuffix: String?): Boolean {
            if (nonSemanticSuffix.isNullOrBlank()) return false
            val normalizedSuffix = nonSemanticSuffix.trim().lowercase()
            val hasGitHashLength = normalizedSuffix.length in 7..10 || normalizedSuffix.length == 40
            if (hasGitHashLength && normalizedSuffix.all { it.isDigit() || it in HEX_CHARS || !it.isLetter() }) return false
            return true
        }

        companion object {

            private val HEX_CHARS = 'a'..'f'
        }
    }

    @Serializable
    data class TimestampLike(
        private val original: PackageVersion.Named,
        val timestampPrefix: String,
        override val stabilityMarker: String?,
        override val nonSemanticSuffix: String?
    ) : NormalizedPackageVersion<PackageVersion.Named>(original), DecoratedVersion {

        private val timestampPrefixWithStabilityMarker = timestampPrefix + (stabilityMarker ?: "")

        override fun compareTo(other: NormalizedPackageVersion<*>): Int =
            when (other) {
                is TimestampLike -> compareByNameAndThenByTimestamp(other)
                is Semantic -> -1
                is Garbage, is Missing -> 1
            }

        private fun compareByNameAndThenByTimestamp(other: TimestampLike): Int {
            val nameComparisonResult = VersionComparatorUtil.compare(
                timestampPrefixWithStabilityMarker,
                other.timestampPrefixWithStabilityMarker,
                ::versionTokenPriorityProvider
            )

            return if (nameComparisonResult == 0) {
                original.compareByTimestamp(other.original)
            } else {
                nameComparisonResult
            }
        }
    }

    @Serializable
    data class Garbage(
        private val original: PackageVersion.Named
    ) : NormalizedPackageVersion<PackageVersion.Named>(original) {

        override fun compareTo(other: NormalizedPackageVersion<*>): Int =
            when (other) {
                is Missing -> 1
                is Garbage -> compareByNameAndThenByTimestamp(other)
                is Semantic, is TimestampLike -> -1
            }

        private fun compareByNameAndThenByTimestamp(other: Garbage): Int {
            val nameComparisonResult = VersionComparatorUtil.compare(original.versionName, other.original.versionName)
            return if (nameComparisonResult == 0) {
                original.compareByTimestamp(other.original)
            } else {
                nameComparisonResult
            }
        }
    }

    @Serializable
    object Missing : NormalizedPackageVersion<PackageVersion.Missing>(PackageVersion.Missing) {

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
            releasedAt == null && other.releasedAt == null -> 0
            releasedAt != null && other.releasedAt == null -> 1
            releasedAt == null && other.releasedAt != null -> -1
            else -> releasedAt!!.compareTo(other.releasedAt!!)
        }

    fun nonSemanticSuffixOrNull(): String? =
        when (this) {
            is Semantic -> nonSemanticSuffix
            is TimestampLike -> nonSemanticSuffix
            is Garbage, is Missing -> null
        }

    interface DecoratedVersion {

        val stabilityMarker: String?

        val nonSemanticSuffix: String?
    }

    companion object {

        suspend fun parseFrom(version: PackageVersion.Named, normalizer: PackageVersionNormalizer): NormalizedPackageVersion<PackageVersion.Named> =
            normalizer.parse(version)

        suspend fun <T : PackageVersion> parseFrom(version: T, normalizer: PackageVersionNormalizer): NormalizedPackageVersion<*> =
            when (version) {
                is PackageVersion.Missing -> Missing
                is PackageVersion.Named -> parseFrom(version, normalizer)
                else -> error("Unknown version type: ${version.javaClass.simpleName}")
            }
    }
}

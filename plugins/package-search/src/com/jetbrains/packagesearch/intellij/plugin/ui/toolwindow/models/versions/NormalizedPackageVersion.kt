package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions

import com.intellij.util.text.VersionComparatorUtil
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion

internal sealed class NormalizedPackageVersion(
    val originalVersion: PackageVersion.Named
) : Comparable<NormalizedPackageVersion> {

    val versionName: String
        get() = originalVersion.versionName

    val displayName: String
        get() = originalVersion.displayName

    val isStable: Boolean
        get() = originalVersion.isStable

    val releasedAt: Long?
        get() = originalVersion.releasedAt

    data class Semantic(
        private val original: PackageVersion.Named,
        val semanticPart: String,
        override val stabilityMarker: String?,
        override val nonSemanticSuffix: String?
    ) : NormalizedPackageVersion(original), DecoratedVersion {

        val semanticPartWithStabilityMarker = semanticPart + (stabilityMarker ?: "")

        override fun compareTo(other: NormalizedPackageVersion): Int =
            when (other) {
                is Semantic -> compareByNameAndThenByTimestamp(other)
                is TimestampLike, is Garbage -> 1
            }

        private fun compareByNameAndThenByTimestamp(other: Semantic): Int {
            val nameComparisonResult = VersionComparatorUtil.compare(
                semanticPartWithStabilityMarker,
                other.semanticPartWithStabilityMarker
            )

            return if (nameComparisonResult == 0) {
                original.compareByTimestamp(other.original)
            } else {
                nameComparisonResult
            }
        }
    }

    data class TimestampLike(
        private val original: PackageVersion.Named,
        val timestampPrefix: String,
        override val stabilityMarker: String?,
        override val nonSemanticSuffix: String?
    ) : NormalizedPackageVersion(original), DecoratedVersion {

        val timestampPrefixWithStabilityMarker = timestampPrefix + (stabilityMarker ?: "")

        override fun compareTo(other: NormalizedPackageVersion): Int =
            when (other) {
                is TimestampLike -> compareByNameAndThenByTimestamp(other)
                is Semantic -> -1
                is Garbage -> 1
            }

        private fun compareByNameAndThenByTimestamp(other: TimestampLike): Int {
            val nameComparisonResult = VersionComparatorUtil.compare(
                timestampPrefixWithStabilityMarker,
                other.timestampPrefixWithStabilityMarker
            )

            return if (nameComparisonResult == 0) {
                original.compareByTimestamp(other.original)
            } else {
                nameComparisonResult
            }
        }
    }

    data class Garbage(
        private val original: PackageVersion.Named
    ) : NormalizedPackageVersion(original) {

        override fun compareTo(other: NormalizedPackageVersion): Int =
            when (other) {
                is Garbage -> compareByNameAndThenByTimestamp(other)
                is Semantic, is TimestampLike -> -1
            }

        private fun compareByNameAndThenByTimestamp(other: Garbage): Int =
            if (VersionComparatorUtil.compare(original.versionName, other.original.versionName) == 0) {
                original.compareByTimestamp(other.original)
            } else {
                VersionComparatorUtil.compare(original.versionName, other.original.versionName)
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

    interface DecoratedVersion {

        val stabilityMarker: String?

        val nonSemanticSuffix: String?
    }

    companion object {

        fun parseFrom(version: PackageVersion.Named): NormalizedPackageVersion =
            PackageVersionNormalizer.parse(version)
    }
}

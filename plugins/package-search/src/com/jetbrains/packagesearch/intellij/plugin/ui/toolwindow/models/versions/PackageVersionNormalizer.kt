package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion.Garbage
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion.Semantic
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion.TimestampLike
import com.jetbrains.packagesearch.intellij.plugin.util.nullIfBlank
import org.apache.commons.collections.map.LRUMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class PackageVersionNormalizer private constructor() {

    private val versionsCache = LRUMap(2_000)

    private val lock = ReentrantReadWriteLock()

    private val HEX_STRING_LETTER_CHARS = 'a'..'f'

    /**
     * Matches a whole string starting with a semantic version. A valid semantic version
     * has [1, 4] numeric components, each up to 5 digits long. Between each component
     * there is a period character.
     *
     * Examples of valid semver: 1, 1.0-whatever, 1.2.3, 2.3.3.0-beta02
     * Examples of invalid semver: 1.0.0.0.0 (too many components), 123456 (component too long)
     *
     * Group 0 matches the whole string, group 1 is the semver minus any suffixes.
     */
    private val SEMVER_REGEX = "^((?:\\d{1,5}\\.){0,3}\\d{1,5}(?!\\.?\\d)).*\$".toRegex(option = RegexOption.IGNORE_CASE)

    /**
     * Extracts stability markers. Must be used on the string that follows a valid semver (see
     * [SEMVER_REGEX]).
     *
     * Stability markers are made up by a separator character (one of: . _ - +), then one of the
     * stability tokens (see list below), followed by an optional separator (one of: . _ -),
     * AND [0, 5] numeric digits. After the digits, there must be a word boundary (most
     * punctuation, except for underscores, qualifies as such).
     *
     * We only support up to two stability markers (arguably, having two already qualifies for
     * the [Garbage] tier, but we have well-known libraries out there that do the two-markers
     * game, now and then, and we need to support those shenanigans).
     *
     * ### Stability tokens
     * We support the following stability tokens:
     *  * `snapshots`*, `snapshot`, `snap`, `s`*
     *  * `preview`, `eap`, `pre`, `p`*
     *  * `develop`*, `dev`*
     *  * `milestone`*, `m`, `build`*
     *  * `alpha`, `a`
     *  * `betta` (yes, there are Bettas out there), `beta`, `b`
     *  * `candidate`*, `rc`
     *  * `sp`
     *  * `release`, `final`, `stable`*, `rel`, `r`
     *
     * Tokens denoted by a `*` are considered as meaningless words by [com.intellij.util.text.VersionComparatorUtil]
     * when comparing without a custom , so sorting may be funky when they appear.
     */
    private val STABILITY_MARKER_REGEX =
        ("^((?:[._\\-+]" +
            "(?:snapshots?|preview|milestone|candidate|release|develop|stable|build|alpha|betta|final|snap|beta|dev|pre|eap|rel|sp|rc|m|r|b|a|p)" +
            "(?:[._\\-]?\\d{1,5})?){1,2})(?:\\b|_)")
            .toRegex(option = RegexOption.IGNORE_CASE)

    fun parse(version: PackageVersion.Named): NormalizedPackageVersion<PackageVersion.Named> {
        @Suppress("UNCHECKED_CAST") // Unfortunately, MRUMap doesn't have type parameters
        val cachedValue =
            lock.read { versionsCache[version] as NormalizedPackageVersion<PackageVersion.Named>? }

        if (cachedValue != null) return cachedValue

        // Before parsing, we rule out git commit hashes — those are garbage as far as we're concerned.
        // The initial step attempts parsing the version as a date(time) string starting at 0; if that fails,
        // and the version is not one uninterrupted alphanumeric blob (trying to catch more garbage), it
        // tries parsing it as a semver; if that fails too, the version name is considered "garbage"
        // (that is, it realistically can't be sorted if not by timestamp, and by hoping for the best).
        val garbage = Garbage(version)
        if (version.looksLikeGitCommitOrOtherHash()) {
            lock.write { versionsCache[version] = garbage }
            return garbage
        }

        val timestampPrefix = VeryLenientDateTimeExtractor.extractTimestampLookingPrefixOrNull(version.versionName)
        if (timestampPrefix != null) {
            val normalized = parseTimestampVersion(version, timestampPrefix)
            lock.write { versionsCache[version] = normalized }
            return normalized
        }

        if (version.isOneBigHexadecimalBlob()) {
            lock.write { versionsCache[version] = garbage }
            return garbage
        }

        val semanticVersionPrefix = version.semanticVersionPrefixOrNull()
        if (semanticVersionPrefix != null) {
            val normalized = parseSemanticVersion(version, semanticVersionPrefix)
            lock.write { versionsCache[version] = normalized }
            return normalized
        }

        lock.write { versionsCache[version] = garbage }
        return garbage
    }

    private fun PackageVersion.Named.looksLikeGitCommitOrOtherHash(): Boolean {
        val hexLookingPrefix = versionName.takeWhile { it.isDigit() || HEX_STRING_LETTER_CHARS.contains(it) }
        return when (hexLookingPrefix.length) {
            7, 40 -> true
            else -> false
        }
    }

    private fun parseTimestampVersion(version: PackageVersion.Named, timestampPrefix: String): NormalizedPackageVersion<PackageVersion.Named> =
        TimestampLike(
            original = version,
            timestampPrefix = timestampPrefix,
            stabilityMarker = version.stabilitySuffixComponentOrNull(timestampPrefix),
            nonSemanticSuffix = version.nonSemanticSuffix(timestampPrefix)
        )

    private fun PackageVersion.Named.isOneBigHexadecimalBlob(): Boolean {
        var hasHexChars = false
        for (char in versionName.lowercase()) {
            when {
                char in HEX_STRING_LETTER_CHARS -> hasHexChars = true
                !char.isDigit() -> return false
            }
        }
        return hasHexChars
    }

    private fun parseSemanticVersion(version: PackageVersion.Named, semanticVersionPrefix: String): NormalizedPackageVersion<PackageVersion.Named> =
        Semantic(
            original = version,
            semanticPart = semanticVersionPrefix,
            stabilityMarker = version.stabilitySuffixComponentOrNull(semanticVersionPrefix),
            nonSemanticSuffix = version.nonSemanticSuffix(semanticVersionPrefix)
        )

    private fun PackageVersion.Named.semanticVersionPrefixOrNull(): String? {
        val groupValues = SEMVER_REGEX.find(versionName)?.groupValues ?: return null
        if (groupValues.size <= 1) return null
        return groupValues[1]
    }

    private fun PackageVersion.Named.stabilitySuffixComponentOrNull(ignoredPrefix: String): String? {
        val groupValues = STABILITY_MARKER_REGEX.find(versionName.substringAfter(ignoredPrefix))
            ?.groupValues ?: return null
        if (groupValues.size <= 1) return null
        return groupValues[1].takeIf { it.isNotBlank() }
    }

    private fun PackageVersion.Named.nonSemanticSuffix(ignoredPrefix: String?): String? {
        val semanticPart = stabilitySuffixComponentOrNull(ignoredPrefix ?: return null)
            ?: ignoredPrefix
        return versionName.substringAfter(semanticPart).nullIfBlank()
    }

    companion object {

        private var INSTANCE: PackageVersionNormalizer? = null

        fun getInstance(): PackageVersionNormalizer {
            if (INSTANCE == null) INSTANCE = PackageVersionNormalizer()
            return INSTANCE!!
        }
    }
}

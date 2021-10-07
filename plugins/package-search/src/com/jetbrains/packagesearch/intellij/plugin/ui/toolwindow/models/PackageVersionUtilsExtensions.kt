package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.packageversionutils.PackageVersionUtils

/**
 * Determines the upgrade candidate version, if any exists in the [availableVersions] list, for [currentVersion].
 * The main difference from the [highestSensibleVersionByNameOrNull] is that this makes sure whatever candidate
 * it returns, if any, is actually temporally more recent than the [currentVersion], not only that it has a
 * higher version name.
 *
 * This checks not only that there is a version with a number that looks greater, but also that the candidate has
 * been released _after_ the current version (only if the current version has a [PackageVersion.releasedAt]).
 *
 * If the current version is not [PackageVersion.Named], then the function returns the same value as
 * [highestSensibleVersionByNameOrNull].
 *
 * If the current version doesn't seem to start with a semantic version, or it does, but it's "weird looking"
 * (this means it has components that are longer than 5 chars), then the function returns the same value as
 * [highestSensibleVersionByNameOrNull]. The "weird looking" heuristic can change at any time, depending on
 * cases out there in the wild that need taking into account.
 *
 * @param currentVersion The version for which to determine the upgrade candidate.
 * @param availableVersions The list of all potential upgrade candidate versions.
 * @return The upgrade candidate version, if any. Null otherwise.
 * @throws IllegalArgumentException If [availableVersions] is empty.
 * @see highestSensibleVersionByNameOrNull
 * @see looksLikeASensibleVersionName
 */
internal fun PackageVersionUtils.upgradeCandidateVersionOrNull(
    currentVersion: NormalizedPackageVersion,
    availableVersions: List<NormalizedPackageVersion>
): NormalizedPackageVersion? {
    val hasReleasedAtInfo = currentVersion.releasedAt != null

    if (hasReleasedAtInfo && currentVersion is NormalizedPackageVersion.Garbage) {
        return availableVersions.maxOrNull()
    }

    var isKnownVersion = false

    val currentSuffix = currentVersion.nonSemanticSuffixOrNull()
    var bestCandidate: NormalizedPackageVersion? = null
    for (candidateVersion in availableVersions) {
        val candidateSuffix = candidateVersion.nonSemanticSuffixOrNull()

        when {
            candidateVersion.versionName == currentVersion.versionName -> isKnownVersion = true
            candidateVersion > currentVersion -> bestCandidate = candidateVersion
//            candidateVersion == currentVersion && candidateSuffix == currentSuffix -> bestCandidate = candidateVersion
//            currentSuffix != null && candidateSuffix == null && bestCandidate?.nonSemanticSuffixOrNull() != currentSuffix -> {
//                bestCandidate = candidateVersion
//            }
        }
    }
    return if (isKnownVersion) bestCandidate else null
}

/**
 * Determines the upgrade candidate version, if any exists in the [availableVersions] list, for [currentVersion].
 * The main difference from the [highestSensibleVersionByNameOrNull] is that this makes sure whatever candidate
 * it returns, if any, is actually temporally more recent than the [currentVersion], not only that it has a
 * higher version name.
 *
 * This checks not only that there is a version with a number that looks greater, but also that the candidate has
 * been released _after_ the current version (only if the current version has a [PackageVersion.releasedAt]).
 *
 * If the current version is not [PackageVersion.Named], then the function returns the same value as
 * [highestSensibleVersionByNameOrNull].
 *
 * If the current version doesn't seem to start with a semantic version, or it does, but it's "weird looking"
 * (this means it has components that are longer than 5 chars), then the function returns the same value as
 * [highestSensibleVersionByNameOrNull]. The "weird looking" heuristic can change at any time, depending on
 * cases out there in the wild that need taking into account.
 *
 * @param currentVersion The version for which to determine the upgrade candidate.
 * @param availableVersions The list of all potential upgrade candidate versions.
 * @return The upgrade candidate version, if any. Null otherwise.
 * @throws IllegalArgumentException If [availableVersions] is empty.
 * @see highestSensibleVersionByNameOrNull
 * @see looksLikeASensibleVersionName
 */
internal fun PackageVersionUtils.upgradeCandidateVersionOrNull(
    currentVersion: PackageVersion,
    availableVersions: List<PackageVersion>
): PackageVersion.Named? {
    if (currentVersion !is PackageVersion.Named) return highestSensibleVersionByNameOrNull(availableVersions)

    // If the current version is not looking like it's sem-ver, or if it is but looks silly (see the
    // looksLikeASensibleVersionName() function) then we treat the current version as garbage — that
    // is, we assume it's not semver, and thus we have no way to compare it; we assume anything else
    // which has a sensible higher version name (if any) is an upgrade.
    if (!currentVersion.looksLikeASensibleVersionName()) return highestSensibleVersionByNameOrNull(availableVersions)

    // If the current version doesn't have a release timestamp, we ignore it in comparisons
    val currentReleasedAt = currentVersion.releasedAt ?: 0

    val candidateVersions = availableVersions.toMutableList()

    while (candidateVersions.isNotEmpty()) {
        val highestCandidate = highestSensibleVersionByNameOrNull(candidateVersions) ?: return null

        val isCandidateMoreRecentThanCurrent =
            highestCandidate.releasedAt != null && highestCandidate.releasedAt > currentReleasedAt
        val isHighestCandidateVersionHigherThanCurrent = highestCandidate > currentVersion

        val isCandidateSuffixSameAsCurrent = false //candidateExtendedSemVer == currentStabilitySuffix

        if (isCandidateMoreRecentThanCurrent &&
            isHighestCandidateVersionHigherThanCurrent &&
            !isCandidateSuffixSameAsCurrent) {
            return highestCandidate
        }

        candidateVersions -= highestCandidate
    }
    return null
}

/**
 * Sort a list of [PackageVersion]s from the "newest" to the "oldest", using heuristics to try
 * and make the sort order somewhat sensible, even when not all version names follow semantic
 * versioning (or some of them "look weird" — see [looksLikeASensibleVersionName]).
 *
 * The resulting list is sorted as follows:
 *  1. All sensible-looking versions, sorted by name descending (if any)
 *  2. All weird-looking versions _with_ a [PackageVersion.releasedAt] value, sorted by timestamp descending (if any)
 *  3. All weird-looking versions _without_ a [PackageVersion.releasedAt] value, sorted by name descending (if any)
 */
internal fun PackageVersionUtils.sortWithHeuristicsDescending(availableVersions: List<PackageVersion>): List<PackageVersion> {
    val sensibleVersions = mutableListOf<PackageVersion>()
    val sillyVersionsWithTimestamp = mutableListOf<PackageVersion>()
    val sillyVersionsWithoutTimestamp = mutableListOf<PackageVersion>()

    availableVersions.forEach {
        when {
            it.looksLikeASensibleVersionName() -> sensibleVersions += it
            it.releasedAt != null -> sillyVersionsWithTimestamp += it
            else -> sillyVersionsWithoutTimestamp += it
        }
    }

    return sensibleVersions.sortedDescending() +
        sillyVersionsWithTimestamp.sortedByDescending { it.releasedAt } +
        sillyVersionsWithoutTimestamp.sortedDescending()
}

/**
 * Determines the highest version in the list that does not "look weird", if any.
 * It's a mildly "smarter" version of [highestVersionByName].
 *
 * See [looksLikeASensibleVersionName] for further details on the "looks weird" logic.
 *
 * @param @param availableVersions The list of versions to look into.
 * @return The highest version by name that looks sensible, if any. Null otherwise.
 * @throws IllegalArgumentException If [availableVersions] is empty.
 * @see looksLikeASensibleVersionName
 * @see highestVersionByName
 */
internal fun PackageVersionUtils.highestSensibleVersionByNameOrNull(availableVersions: List<PackageVersion>): PackageVersion.Named? {
    require(availableVersions.isNotEmpty()) { "Cannot find highest version in an empty list" }

    return availableVersions.asSequence()
        .filterIsInstance<PackageVersion.Named>()
        .filter { it.looksLikeASensibleVersionName() }
        .maxOrNull()
}

/**
 * Returns `true` if the version name seems sensible, or `false` if it "looks weird".
 * [PackageVersion.Missing] don't have a version name and will always return `false`.
 *
 * The definition of the "weird looking" concept is currently that it does not start with a semantic
 * version, or that if it does, any of the semantic version components are longer than 5 characters.
 * The "weird looking" heuristic can change at any time, depending on cases out there in the wild
 * that need taking into account.
 *
 * @return True when the version name looks sensible, false otherwise.
 */
internal fun PackageVersion.looksLikeASensibleVersionName(): Boolean {
    if (this !is PackageVersion.Named) return false

    return true // TODO reimplement this
//    val semVer = semanticVersionComponent()?.semanticVersion ?: return false
//    return semVer.splitToSequence('.').none { it.length > 5 }
}

/**
 * Determines the version in the list which has the highest version number. This function does
 * not have any smarts to it; this means that implausible version names aren't filtered out (see the
 * [looksLikeASensibleVersionName] documentation for a definition).
 *
 * This may not be what you want to use. You're likely looking for [highestSensibleVersionByNameOrNull].
 *
 * @param availableVersions The list of versions to look into.
 * @return The highest version by name.
 * @throws IllegalArgumentException If [availableVersions] is empty.
 * @see highestSensibleVersionByNameOrNull
 * @see looksLikeASensibleVersionName
 */
internal fun PackageVersionUtils.highestVersionByName(availableVersions: List<PackageVersion>): PackageVersion.Named {
    require(availableVersions.isNotEmpty()) { "Cannot find highest version in an empty list" }

    val highestVersion = availableVersions.maxOrNull() ?: error("An installed package must always have at least one usage")
    return highestVersion as? PackageVersion.Named ?: error("We shouldn't have Missing versions here")
}

private val ignoredTokens = listOf(
    "jre\\d+".toRegex(RegexOption.IGNORE_CASE),
    "jdk\\d+".toRegex(RegexOption.IGNORE_CASE),
)

private fun PackageVersion.Named.stripIgnoredTokens(): PackageVersion.Named {
    var stripped = versionName
    for (ignoredToken in ignoredTokens) {
        stripped = stripped
    }
    return copy(versionName = stripped)
}

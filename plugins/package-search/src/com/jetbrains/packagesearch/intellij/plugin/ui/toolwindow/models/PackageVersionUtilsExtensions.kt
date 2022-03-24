package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.VersionNameComparator
import com.jetbrains.packagesearch.packageversionutils.PackageVersionUtils
import kotlin.math.sign

/**
 * Determines the upgrade candidate version, if any exists in the [availableVersions] list, for [currentVersion].
 * The main difference from the [highestSensibleVersionByNameOrNull] is that this makes sure whatever candidate
 * it returns, if any, is "higher" than the [currentVersion], not only that it is the one with the highest version
 * name in the list.
 *
 * If the current version is not a [NormalizedPackageVersion.Semantic], then the function returns the highest
 * semantic version in the [availableVersions] list, if any.
 *
 * If the current version is a [NormalizedPackageVersion.Semantic], then the function first tries to find a
 * candidate with the same non-semantic suffix (which may be null/empty). If there is no candidate with the
 * same suffix, it picks one without suffix. If there's no candidate without suffix, then it returns the one
 * with the highest version name (and thus, the highest suffix).
 *
 * If there's no candidate which satisfies the criteria, the function returns `null`.
 *
 * @param currentVersion The version for which to determine the upgrade candidate.
 * @param availableVersions The list of all potential upgrade candidate versions.
 * @return The upgrade candidate version, if any. Null otherwise.
 * @throws IllegalArgumentException If [availableVersions] is empty.
 * @see highestSensibleVersionByNameOrNull
 * @see VersionNameComparator
 */
internal fun PackageVersionUtils.upgradeCandidateVersionOrNull(
    currentVersion: NormalizedPackageVersion<*>,
    availableVersions: List<NormalizedPackageVersion<*>>
): NormalizedPackageVersion<*>? {
    require(availableVersions.isNotEmpty()) { "Cannot find upgrades when there are no available versions" }

    val availableSemanticVersions = availableVersions.filterIsInstance<NormalizedPackageVersion.Semantic>()
        .sortedDescending()

    return when (currentVersion) {
        is NormalizedPackageVersion.Semantic -> getUpgradeSemanticVersionOrNull(availableSemanticVersions, currentVersion)
        else -> {
            // A Semantic version is always better than the current one, so we should prefer that
            if (availableSemanticVersions.isNotEmpty()) {
                availableSemanticVersions.filter { it.nonSemanticSuffixOrNull().isNullOrBlank() }.maxOrNull()
            } else {
                availableVersions.maxOrNull()?.takeIf { it > currentVersion }
            }
        }
    }
}

private fun getUpgradeSemanticVersionOrNull(
    availableSemanticVersions: List<NormalizedPackageVersion.Semantic>,
    currentVersion: NormalizedPackageVersion.Semantic
): NormalizedPackageVersion<out PackageVersion>? {
    // Finding the upgrade version for semantic versions is a multi-step process:
    //  1. Find a version with a higher semantic version part (including stability modifiers)
    //  2. If there's one with the same non-semantic suffix, pick that
    //  3. If there's one without a semantic suffix, pick that
    //  4. Pick whatever looks "highest", as defined by comparing the two versions
    // Note that if the version we're comparing to is not semantic, it's not a candidate.
    val candidates = mutableSetOf<NormalizedPackageVersion.Semantic>()

    for (candidateVersion in availableSemanticVersions) {
        // The result is >= 0 if the current version's semver is larger than the candidate's
        val result = VersionNameComparator.compare(
            first = currentVersion.semanticPartWithStabilityMarker,
            second = candidateVersion.semanticPartWithStabilityMarker
        )
        when {
            result == 0 || result.sign == 1 -> break // The list is sorted: nothing after this will be > the current one
            else -> candidates += candidateVersion
        }
    }

    if (candidates.isEmpty()) return null

    val currentSuffix = currentVersion.nonSemanticSuffixOrNull()
    val upgradeWithSameSuffix = candidates.find { it.nonSemanticSuffix == currentSuffix }
    if (upgradeWithSameSuffix != null) return upgradeWithSameSuffix

    return candidates.find { it.nonSemanticSuffix.isNullOrBlank() }
        ?: candidates.maxOrNull()
}

/**
 * Determines the highest version in the list that does not "look weird", if any.
 *
 * See [NormalizedPackageVersion] for further details on the "looks weird" logic,
 * which in this case maps to not being a [NormalizedPackageVersion.Garbage].
 *
 * @param @param availableVersions The list of versions to look into.
 * @return The highest version by name that looks sensible, if any. Null otherwise.
 * @throws IllegalArgumentException If [availableVersions] is empty.
 * @see NormalizedPackageVersion
 */
internal fun PackageVersionUtils.highestSensibleVersionByNameOrNull(
    availableVersions: List<NormalizedPackageVersion<*>>
): NormalizedPackageVersion<*>? {

    require(availableVersions.isNotEmpty()) { "Cannot find highest version in an empty list" }

    return availableVersions.asSequence()
        .filter { it !is NormalizedPackageVersion.Garbage }
        .maxOrNull()
}

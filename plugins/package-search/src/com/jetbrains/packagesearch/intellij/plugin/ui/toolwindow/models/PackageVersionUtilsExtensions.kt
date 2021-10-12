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
internal fun PackageVersionUtils.highestSensibleVersionByNameOrNull(availableVersions: List<NormalizedPackageVersion>): NormalizedPackageVersion? {
    require(availableVersions.isNotEmpty()) { "Cannot find highest version in an empty list" }

    return availableVersions.asSequence()
        .filter { it !is NormalizedPackageVersion.Garbage }
        .maxOrNull()
}

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

@file:Suppress("UnusedReceiverParameter") // Used to namespace the functions

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.VersionNameComparator
import org.jetbrains.packagesearch.packageversionutils.PackageVersionUtils
import kotlin.math.sign

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

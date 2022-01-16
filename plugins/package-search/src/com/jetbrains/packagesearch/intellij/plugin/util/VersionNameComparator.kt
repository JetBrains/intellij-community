package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.util.text.VersionComparatorUtil
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion

/**
 * Compares two raw version names, best-effort. If at all possible, you should use the
 * [NormalizedPackageVersion] to compare versions with a lot of additional heuristics,
 * including prioritizing semantic version names over non-semantic ones.
 *
 * This expands on what [VersionComparatorUtil] provide, so you should prefer this to
 * [VersionComparatorUtil.COMPARATOR].
 *
 * @see versionTokenPriorityProvider
 * @see VersionComparatorUtil
 */
internal object VersionNameComparator : Comparator<String> {

    override fun compare(first: String?, second: String?): Int =
        VersionComparatorUtil.compare(first, second, ::versionTokenPriorityProvider)
}

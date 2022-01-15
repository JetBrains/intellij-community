package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion

internal object PackageVersionComparator : Comparator<PackageVersion> {

    override fun compare(first: PackageVersion?, second: PackageVersion?): Int {
        @Suppress("KotlinConstantConditions") // True, but it's clearer if it's explicitly spelled out
        when {
            first == null && second != null -> return -1
            first != null && second == null -> return 1
            first == null && second == null -> return 0
            first is PackageVersion.Missing && second !is PackageVersion.Missing -> return -1
            first !is PackageVersion.Missing && second is PackageVersion.Missing -> return 1
            first is PackageVersion.Missing && second is PackageVersion.Missing -> return 0
        }

        return compareNamed(first as PackageVersion.Named, second as PackageVersion.Named)
    }

    private fun compareNamed(first: PackageVersion.Named, second: PackageVersion.Named): Int {
        return NormalizedPackageVersion.parseFrom(first).compareTo(NormalizedPackageVersion.parseFrom(second))
    }
}

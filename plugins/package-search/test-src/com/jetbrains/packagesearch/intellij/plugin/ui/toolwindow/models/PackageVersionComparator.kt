package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.PackageVersionNormalizer
import kotlinx.coroutines.runBlocking

internal object PackageVersionComparator : Comparator<PackageVersion> {

    val normalizer = PackageVersionNormalizer()

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
        return runBlocking { NormalizedPackageVersion.parseFrom(first, normalizer).compareTo(NormalizedPackageVersion.parseFrom(second, normalizer)) }
    }
}

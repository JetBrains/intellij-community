package com.jetbrains.packagesearch.intellij.plugin.data

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackagesToUpgrade

internal data class PackageUpgradeCandidates(
    val stableUpgrades: PackagesToUpgrade,
    val allUpgrades: PackagesToUpgrade
) {

    fun getPackagesToUpgrade(onlyStable: Boolean) = if (onlyStable) stableUpgrades else allUpgrades

    companion object {

        val EMPTY = PackageUpgradeCandidates(PackagesToUpgrade.EMPTY, PackagesToUpgrade.EMPTY)
    }
}

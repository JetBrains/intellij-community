package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

internal data class SelectedPackageModel<T : PackageModel>(
    val packageModel: T,
    val selectedVersion: PackageVersion,
    val selectedScope: PackageScope,
    val mixedBuildSystemTargets: Boolean
)

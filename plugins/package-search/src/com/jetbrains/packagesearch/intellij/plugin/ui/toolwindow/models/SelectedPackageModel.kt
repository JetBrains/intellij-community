package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

// TODO should be a sealed class
internal data class SelectedPackageModel<T : PackageModel>(
    val packageModel: T,
    val selectedVersion: PackageVersion,
    val selectedScope: PackageScope,
    val mixedBuildSystemTargets: Boolean
)

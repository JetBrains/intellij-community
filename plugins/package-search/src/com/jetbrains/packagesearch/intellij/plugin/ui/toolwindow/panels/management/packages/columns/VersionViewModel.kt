package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion

internal sealed class VersionViewModel<T : PackageModel> {

    abstract val packageModel: T
    abstract val selectedVersion: PackageVersion

    data class InstalledPackage(
        override val packageModel: PackageModel.Installed,
        override val selectedVersion: PackageVersion
    ) : VersionViewModel<PackageModel.Installed>()

    data class InstallablePackage(
        override val packageModel: PackageModel.SearchResult,
        override val selectedVersion: PackageVersion
    ) : VersionViewModel<PackageModel.SearchResult>()
}

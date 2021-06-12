package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope

internal sealed class ScopeViewModel<T : PackageModel> {

    abstract val selectedScope: PackageScope
    abstract val packageModel: T

    data class InstalledPackage(
        override val packageModel: PackageModel.Installed,
        val installedScopes: List<PackageScope>,
        val defaultScope: PackageScope,
        override val selectedScope: PackageScope
    ) : ScopeViewModel<PackageModel.Installed>() {

        init {
            require(installedScopes.isNotEmpty()) { "An installed package must have at least one installed scope" }
        }
    }

    data class InstallablePackage(
        override val packageModel: PackageModel.SearchResult,
        val availableScopes: List<PackageScope>,
        val defaultScope: PackageScope,
        override val selectedScope: PackageScope
    ) : ScopeViewModel<PackageModel.SearchResult>() {

        init {
            require(availableScopes.isNotEmpty()) { "A package must have at least one available scope" }
        }
    }
}

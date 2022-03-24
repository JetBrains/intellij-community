package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion

internal data class SearchResultUiState(
    val selectedVersion: NormalizedPackageVersion<*>?,
    val selectedScope: PackageScope?
)

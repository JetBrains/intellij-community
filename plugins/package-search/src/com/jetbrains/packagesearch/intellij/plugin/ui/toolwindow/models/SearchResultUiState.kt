package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

internal data class SearchResultUiState(
    val selectedVersion: PackageVersion?,
    val selectedScope: PackageScope?
)

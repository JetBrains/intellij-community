package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

internal data class FilterOptions(
    val onlyStable: Boolean = false,
    val onlyKotlinMultiplatform: Boolean = false,
    val onlyRepositoryIds: Set<String> = emptySet()
)

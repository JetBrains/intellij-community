package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

internal data class DataStatus(
    val isSearching: Boolean = false,
    val isRefreshingData: Boolean = false,
    val isExecutingOperations: Boolean = false
) {

    val isBusy = isRefreshingData || isSearching || isExecutingOperations

    override fun toString() = "DataStatus(isBusy=$isBusy " +
        "[isSearching=$isSearching, isRefreshingData=$isRefreshingData, isExecutingOperations=$isExecutingOperations])"
}

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

internal interface SearchResultStateSetter {

    suspend fun setSearchResultState(
        searchResult: PackageModel.SearchResult,
        newVersion: PackageVersion? = null,
        newScope: PackageScope? = null
    )
}

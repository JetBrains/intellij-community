package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

interface SearchClient {

    fun setSearchQuery(query: String)
    fun setOnlyStable(onlyStable: Boolean)
    fun setOnlyKotlinMultiplatform(onlyKotlinMultiplatform: Boolean)
}

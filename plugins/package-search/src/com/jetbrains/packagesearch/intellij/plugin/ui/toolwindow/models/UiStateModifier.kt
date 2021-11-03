package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import kotlinx.coroutines.flow.Flow

internal interface UiStateModifier {

    fun setSearchQuery(query: String)
    fun setTargetModules(modules: TargetModules)
}

internal interface UiStateSource {

    val searchQueryFlow: Flow<String>
    val targetModulesFlow: Flow<TargetModules>
}

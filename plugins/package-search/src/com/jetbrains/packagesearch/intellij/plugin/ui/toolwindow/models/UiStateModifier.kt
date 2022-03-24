package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.buildsystem.model.unified.UnifiedDependency
import kotlinx.coroutines.flow.Flow

internal interface UiStateModifier {

    fun setSearchQuery(query: String)
    fun setTargetModules(modules: TargetModules)
    fun setDependency(coordinates: UnifiedDependency)
}

internal interface UiStateSource {

    val searchQueryFlow: Flow<String>
    val targetModulesFlow: Flow<TargetModules>
    val selectedDependencyFlow: Flow<UnifiedDependency>
}

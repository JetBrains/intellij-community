package com.jetbrains.packagesearch.intellij.plugin.ui

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateModifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateSource
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.shareIn

@Service(Service.Level.PROJECT)
internal class UiCommandsService(project: Project) : UiStateModifier, UiStateSource, CoroutineScope by project.lifecycleScope {

    private val programmaticSearchQueryChannel = Channel<String>(onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val programmaticTargetModulesChannel = Channel<TargetModules>(onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val programmaticSelectedDependencyChannel = Channel<UnifiedDependency>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val searchQueryFlow: Flow<String> = programmaticSearchQueryChannel.consumeAsFlow()
        .shareIn(this, SharingStarted.Eagerly)

    override val targetModulesFlow: Flow<TargetModules> = programmaticTargetModulesChannel.consumeAsFlow()
        .shareIn(this, SharingStarted.Eagerly)

    override val selectedDependencyFlow: Flow<UnifiedDependency> = programmaticSelectedDependencyChannel.consumeAsFlow()
        .shareIn(this, SharingStarted.Eagerly)

    override fun setSearchQuery(query: String) {
        programmaticSearchQueryChannel.trySend(query)
    }

    override fun setTargetModules(modules: TargetModules) {
        programmaticTargetModulesChannel.trySend(modules)
    }

    override fun setDependency(coordinates: UnifiedDependency) {
        programmaticSelectedDependencyChannel.trySend(coordinates)
    }
}

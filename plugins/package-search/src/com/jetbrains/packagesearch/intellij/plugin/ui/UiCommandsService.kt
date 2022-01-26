package com.jetbrains.packagesearch.intellij.plugin.ui

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
import kotlinx.coroutines.launch

internal class UiCommandsService(project: Project) : UiStateModifier, UiStateSource, CoroutineScope by project.lifecycleScope {

    private val programmaticSearchQueryChannel = Channel<String>(onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val programmaticTargetModulesChannel = Channel<TargetModules>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val searchQueryFlow: Flow<String> = programmaticSearchQueryChannel.consumeAsFlow()
        .shareIn(this, SharingStarted.Eagerly)

    override val targetModulesFlow: Flow<TargetModules> = programmaticTargetModulesChannel.consumeAsFlow()
        .shareIn(this, SharingStarted.Eagerly)

    override fun setSearchQuery(query: String) {
        launch { programmaticSearchQueryChannel.send(query) }
    }

    override fun setTargetModules(modules: TargetModules) {
        launch { programmaticTargetModulesChannel.send(modules) }
    }
}

/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateModifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateSource
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.shareIn

@Service(Service.Level.PROJECT)
internal class PkgsUiCommandsService(project: Project) : UiStateModifier, UiStateSource {

    private val programmaticSearchQueryChannel = Channel<String>(onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val programmaticTargetModulesChannel = Channel<TargetModules>(onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val programmaticSelectedDependencyChannel = Channel<UnifiedDependency>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val searchQueryFlow: Flow<String> = programmaticSearchQueryChannel.consumeAsFlow()
        .shareIn(project.lifecycleScope, SharingStarted.Eagerly)

    override val targetModulesFlow: Flow<TargetModules> = programmaticTargetModulesChannel.consumeAsFlow()
        .shareIn(project.lifecycleScope, SharingStarted.Eagerly)

    override val selectedDependencyFlow: Flow<UnifiedDependency> = programmaticSelectedDependencyChannel.consumeAsFlow()
        .shareIn(project.lifecycleScope, SharingStarted.Eagerly)

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

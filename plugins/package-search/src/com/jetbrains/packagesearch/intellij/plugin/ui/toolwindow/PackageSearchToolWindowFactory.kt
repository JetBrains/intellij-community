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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.packagesearch.PackageSearchIcons
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.toolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take

internal class PackageSearchToolWindowFactory : ToolWindowFactory, DumbAware {
    companion object {

        internal val ToolWindowId = PackageSearchBundle.message("toolwindow.stripe.Dependencies")

        private fun getToolWindow(project: Project) = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId)

        fun activateToolWindow(project: Project, action: () -> Unit) {
            getToolWindow(project)?.activate(action, true, true)
        }
    }

    override fun isApplicable(project: Project): Boolean {
        if (PluginEnvironment.isTestEnvironment) {
            return false
        }

        val isAvailable = DependenciesToolwindowTabProvider.extensions(project).isNotEmpty()

        if (!isAvailable) {
            DependenciesToolwindowTabProvider.availableTabsFlow(project)
                .filter { it.isNotEmpty() }
                .take(1)
                .map {
                    RegisterToolWindowTask.closable(
                        ToolWindowId,
                        PackageSearchBundle.messagePointer("toolwindow.stripe.Dependencies"),
                        PackageSearchIcons.ArtifactSmall
                    )
                }
                .map { toolWindowTask -> project.toolWindowManager.registerToolWindow(toolWindowTask) }
                .onEach { toolWindow -> toolWindow.initialize(project) }
                .flowOn(Dispatchers.toolWindowManager(project))
                .launchIn(project.lifecycleScope)
        }

        return isAvailable
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) = toolWindow.initialize(project)
}


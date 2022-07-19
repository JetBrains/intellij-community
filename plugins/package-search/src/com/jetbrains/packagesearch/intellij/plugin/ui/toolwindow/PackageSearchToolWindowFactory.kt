// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.packagesearch.PackageSearchIcons
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext

internal class PackageSearchToolWindowFactory : ProjectPostStartupActivity {
    companion object {
        internal val ToolWindowId = PackageSearchBundle.message("toolwindow.stripe.Dependencies")

        private fun getToolWindow(project: Project) = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId)

        fun activateToolWindow(project: Project, action: () -> Unit) {
            getToolWindow(project)?.activate(action, true, true)
        }
    }

    override suspend fun execute(project: Project) {
        withContext(Dispatchers.toolWindowManager(project)) {
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
                .map { toolWindowTask -> ToolWindowManager.getInstance(project).registerToolWindow(toolWindowTask) }
                .collect { toolWindow -> toolWindow.initialize(project) }
        }
    }
}

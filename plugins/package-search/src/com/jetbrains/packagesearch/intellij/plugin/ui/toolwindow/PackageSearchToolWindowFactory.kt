package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.openapi.application.EDT
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
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import com.jetbrains.packagesearch.intellij.plugin.util.toolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take

class PackageSearchToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {

        private val ToolWindowId = PackageSearchBundle.message("toolwindow.stripe.Dependencies")

        private fun getToolWindow(project: Project) = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId)

        fun activateToolWindow(project: Project, action: () -> Unit) {
            getToolWindow(project)?.activate(action, true, true)
        }
    }

    override fun isApplicable(project: Project) = when {
        PluginEnvironment.isTestEnvironment -> false
        else -> {
            val isAvailable = project.packageSearchProjectService.projectModulesStateFlow.value.isNotEmpty()

            if (!isAvailable) {
                project.packageSearchProjectService.projectModulesStateFlow
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
                    .flowOn(Dispatchers.EDT)
                    .launchIn(project.lifecycleScope)
            }
            isAvailable
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) = toolWindow.initialize(project)
}

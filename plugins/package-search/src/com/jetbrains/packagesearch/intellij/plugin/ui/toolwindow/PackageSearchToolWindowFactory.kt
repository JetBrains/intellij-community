@file:Suppress("EXPERIMENTAL_IS_NOT_ENABLED", "EXPERIMENTAL_API_USAGE")

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.packagesearch.PackageSearchIcons
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.util.AppUI
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchModulesChangesFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take

class PackageSearchToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {

        private val ToolWindowId = PackageSearchBundle.message("packagesearch.ui.toolwindow.title")

        private fun getToolWindow(project: Project) = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId)

        fun activateToolWindow(project: Project) {
            getToolWindow(project)?.activate {}
        }

        fun activateToolWindow(project: Project, action: () -> Unit) {
            getToolWindow(project)?.activate(action, true, true)
        }

        fun toggleToolWindow(project: Project) {
            getToolWindow(project)?.let {
                if (it.isVisible) {
                    it.hide { }
                } else {
                    it.activate(null, true, true)
                }
            }
        }
    }

    /**
     * Used as entrypoint for Package Search Plugin. It registers the listeners in the
     * plugin lifecycle to create the toolbar if needed. Since `isApplicable` is called only once at plugin
     * initialization, it cannot be used to create the tool window later when needed.
     */
    override fun isApplicable(project: Project): Boolean {
        project.packageSearchModulesChangesFlow
            .filter { it.isNotEmpty() }
            .take(1)
            .flowOn(Dispatchers.Default)
            .map {
                RegisterToolWindowTask.closable(
                    ToolWindowId,
                     PackageSearchBundle.messagePointer("toolwindow.stripe.Packages"),
                    PackageSearchIcons.ArtifactSmall
                )
            }
            .map { toolWindowTask -> ToolWindowManager.getInstance(project).registerToolWindow(toolWindowTask) }
            .onEach { toolWindow -> project.service<PackageSearchToolWindowService>().initialize(toolWindow) }
            .flowOn(Dispatchers.AppUI)
            .launchIn(project.lifecycleScope)

        return false
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Intentionally left blank. Read `isApplicable` KDoc above.
    }
}

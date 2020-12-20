package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbUnawareHider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import javax.swing.JLabel

class PackageSearchToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {

        private val ToolWindowId = PackageSearchBundle.message("packagesearch.ui.toolwindow.title")

        val ToolWindowModelKey = Key.create<PackageSearchToolWindowModel>("PackageSearch.Management.Model")

        private fun getToolWindow(project: Project) = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId)

        fun activateToolWindow(project: Project) {
            PackageSearchEventsLogger.onToolWindowOpen(project)
            getToolWindow(project)?.activate {}
        }

        fun activateToolWindow(project: Project, action: () -> Unit) {
            PackageSearchEventsLogger.onToolWindowOpen(project)
            getToolWindow(project)?.activate(action, true, true)
        }

        fun toggleToolWindow(project: Project) {
            getToolWindow(project)?.let {
                if (it.isVisible) {
                    PackageSearchEventsLogger.onToolWindowOpen(project)
                    it.hide { }
                } else {
                    PackageSearchEventsLogger.onToolWindowClose(project)
                    it.activate(null, true, true)
                }
            }
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // On first load, show "unavailable while indices are built"
        toolWindow.contentManager.addContent(
            ContentFactory.SERVICE.getInstance().createContent(
                DumbUnawareHider(JLabel()).apply { setContentVisible(false) },
                PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.title"), false
            ).apply {
                isCloseable = false
            })

        // Once indices have been built once, show tool window forever
        DumbService.getInstance(project).runWhenSmart {
            ServiceManager.getService(project, PackageSearchToolWindowAvailabilityService::class.java).initialize(toolWindow)
        }
    }
}

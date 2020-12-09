package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.ProjectTopics
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbUnawareHider
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.SimpleToolWindowWithTwoToolbarsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.PackageManagementPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories.RepositoryManagementPanel
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JLabel

class PackageSearchToolWindowAvailabilityService(val project: Project) {

    private var toolWindow: ToolWindow? = null
    private var toolWindowContentsCreated = false
    private var wasAvailable = false

    fun initialize(toolWindow: ToolWindow) {
        this.toolWindow = toolWindow

        setAvailabilityBasedOnProjectModules(project)
        startMonitoring()
    }

    private fun startMonitoring() {
        project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) {
                setAvailabilityBasedOnProjectModules(project)
            }
        })

        project.messageBus.connect().subscribe(ProjectTopics.MODULES, object : ModuleListener {
            override fun moduleAdded(p: Project, module: Module) {
                setAvailabilityBasedOnProjectModules(project)
            }

            override fun moduleRemoved(p: Project, module: Module) {
                setAvailabilityBasedOnProjectModules(project)
            }
        })
    }

    private fun setAvailabilityBasedOnProjectModules(project: Project) {
        val isAvailable = ProjectModuleProvider.obtainAllProjectModulesFor(project).any()
        toolWindow?.let {
            if (wasAvailable != isAvailable || !toolWindowContentsCreated) {
                createToolWindowContents(it, isAvailable)
            }
        }
    }

    private fun createToolWindowContents(toolWindow: ToolWindow, isAvailable: Boolean) {
        toolWindowContentsCreated = true
        wasAvailable = isAvailable

        toolWindow.title = PackageSearchBundle.message("packagesearch.ui.toolwindow.title")

        val contentFactory = ContentFactory.SERVICE.getInstance()
        val contentManager = toolWindow.contentManager

        contentManager.removeAllContents(false)

        if (!isAvailable) {
            contentManager.addContent(ContentFactory.SERVICE.getInstance().createContent(
                DumbUnawareHider(JLabel()).apply {
                    setContentVisible(false)
                    emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.noModules")
                },
                PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.title"), false
            ).apply {
                isCloseable = false
            })

            return
        }

        val lifetime = project.createLifetime()
        val model = PackageSearchToolWindowModel(project, lifetime)
        project.putUserData(PackageSearchToolWindowFactory.ToolWindowModelKey, model)

        val panels = mutableListOf(
            PackageManagementPanel(model),
            RepositoryManagementPanel(model)
        )

        for (panel in panels) {
            val panelContent = panel.content // should be executed before toolbars
            val toolbar = panel.toolbar
            val topToolbar = panel.topToolbar
            if (topToolbar == null) {
                contentManager.addTab(panel.title, panelContent, toolbar)
            } else {
                val content = contentFactory.createContent(
                    SimpleToolWindowWithTwoToolbarsPanel(
                        toolbar!!,
                        topToolbar,
                        panelContent
                    ), panel.title, false
                )

                content.isCloseable = false
                contentManager.addContent(content)
            }
        }
    }

    private fun ContentManager.addTab(@TabTitle title: String, content: JComponent, toolbar: JComponent?) {
        addContent(ContentFactory.SERVICE.getInstance().createContent(null, title, false).apply {
            component = SimpleToolWindowPanel(false).setProvideQuickActions(true).apply {
                setContent(content)
                toolbar?.let { setToolbar(it) }

                isCloseable = false
            }
        })
    }
}

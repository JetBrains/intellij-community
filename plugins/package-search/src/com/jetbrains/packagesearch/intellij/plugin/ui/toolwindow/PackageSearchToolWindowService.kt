package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.ProjectTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbUnawareHider
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.HasToolWindowActions
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.SimpleToolWindowWithToolWindowActionsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.SimpleToolWindowWithTwoToolbarsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.PackageManagementPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories.RepositoryManagementPanel
import com.jetbrains.packagesearch.intellij.plugin.util.FeatureFlags
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.dataService
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JLabel

internal class PackageSearchToolWindowService(val project: Project) : Disposable {

    private lateinit var toolWindow: ToolWindow
    private var toolWindowContentsCreated = false
    private var wasAvailable = false

    fun initialize(toolWindow: ToolWindow) {
        this.toolWindow = toolWindow

        setAvailabilityBasedOnProjectModules(project)
        startMonitoring()
    }

    private fun setAvailabilityBasedOnProjectModules(project: Project) {
        if (!::toolWindow.isInitialized) return

        val isAvailable = ProjectModuleProvider.obtainAllProjectModulesFor(project).any()
        if (wasAvailable != isAvailable || !toolWindowContentsCreated) {
            createToolWindowContents(toolWindow, isAvailable)
        }
    }

    private val panels = mutableListOf<PackageSearchPanelBase>()

    private fun createToolWindowContents(toolWindow: ToolWindow, isAvailable: Boolean) {
        toolWindowContentsCreated = true
        wasAvailable = isAvailable

        toolWindow.title = PackageSearchBundle.message("packagesearch.ui.toolwindow.title")

        val contentFactory = ContentFactory.SERVICE.getInstance()
        val contentManager = toolWindow.contentManager

        contentManager.removeAllContents(false)

        if (!isAvailable) {
            renderUnavailableUi(contentManager)
            return
        }

        if (toolWindow is ToolWindowEx) {
            registerSelectionChangedListener(contentManager, toolWindow)
        }

        val rootModel = project.dataService()
        panels += PackageManagementPanel(
            rootDataProvider = rootModel,
            selectedPackageSetter = rootModel,
            targetModuleSetter = rootModel,
            searchClient = rootModel,
            operationExecutor = rootModel,
            lifetimeProvider = rootModel
        )

        if (FeatureFlags.showRepositoriesTab) {
            panels += RepositoryManagementPanel(
                rootDataProvider = rootModel,
                lifetimeProvider = rootModel
            )
        }

        for (panel in panels) {
            initializePanel(panel, contentManager, contentFactory)
        }
    }

    private fun renderUnavailableUi(contentManager: ContentManager) {
        contentManager.addContent(
            ContentFactory.SERVICE.getInstance().createContent(
                DumbUnawareHider(JLabel()).apply {
                    setContentVisible(false)
                    emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.noModules")
                },
                PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.title"), false
            ).apply {
                isCloseable = false
            }
        )
    }

    private fun registerSelectionChangedListener(
        contentManager: ContentManager,
        toolWindow: ToolWindowEx
    ) {
        contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                toolWindow.setAdditionalGearActions(null)
                toolWindow.setTitleActions(emptyList())

                val panel = event.content.component as? HasToolWindowActions
                panel?.gearActions?.let {
                    toolWindow.setAdditionalGearActions(it)
                }
                panel?.titleActions?.let {
                    toolWindow.setTitleActions(it.asList())
                }
            }
        })
    }

    private fun initializePanel(
        panel: PackageSearchPanelBase,
        contentManager: ContentManager,
        contentFactory: ContentFactory
    ) {
        val panelContent = panel.content // should be executed before toolbars
        val toolbar = panel.toolbar
        val topToolbar = panel.topToolbar
        val gearActions = panel.gearActions
        val titleActions = panel.titleActions

        if (topToolbar == null) {
            contentManager.addTab(panel.title, panelContent, toolbar, gearActions, titleActions)
        } else {
            val content = contentFactory.createContent(
                toolbar?.let {
                    SimpleToolWindowWithTwoToolbarsPanel(
                        it,
                        topToolbar,
                        gearActions,
                        titleActions,
                        panelContent
                    )
                },
                panel.title,
                false
            )

            content.isCloseable = false
            contentManager.addContent(content)
        }
    }

    private fun ContentManager.addTab(
        @Nls title: String,
        content: JComponent,
        toolbar: JComponent?,
        gearActions: ActionGroup?,
        titleActions: Array<AnAction>?
    ) {
        addContent(
            ContentFactory.SERVICE.getInstance().createContent(null, title, false).apply {
                component = SimpleToolWindowWithToolWindowActionsPanel(gearActions, titleActions, false).apply {
                    setProvideQuickActions(true)
                    setContent(content)
                    toolbar?.let { setToolbar(it) }

                    isCloseable = false
                }
            }
        )
    }

    private fun startMonitoring() {
        project.messageBus.connect(this).subscribe(
            ProjectTopics.PROJECT_ROOTS,
            object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) {
                    setAvailabilityBasedOnProjectModules(project)
                }
            }
        )

        project.messageBus.connect(this).subscribe(
            ProjectTopics.MODULES,
            object : ModuleListener {
                override fun moduleAdded(p: Project, module: Module) {
                    setAvailabilityBasedOnProjectModules(project)
                }

                override fun moduleRemoved(p: Project, module: Module) {
                    setAvailabilityBasedOnProjectModules(project)
                }
            }
        )
    }

    override fun dispose() {
        logDebug("PackageSearchToolWindowService#dispose()") { "Disposing PackageSearchToolWindowService..." }
        for (panel in panels) {
            if (panel is Disposable) Disposer.dispose(panel)
        }

        if (::toolWindow.isInitialized && !toolWindow.isDisposed) {
            toolWindow.remove()
        }
    }
}

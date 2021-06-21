package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.ProjectTopics
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
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
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.HasToolWindowActions
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.SimpleToolWindowWithToolWindowActionsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.SimpleToolWindowWithTwoToolbarsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.PackageManagementPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories.RepositoryManagementPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.util.AppUI
import com.jetbrains.packagesearch.intellij.plugin.util.FeatureFlags
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchDataService
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchModulesChangesFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal class PackageSearchToolWindowService(val project: Project) : Disposable {

    private lateinit var toolWindow: ToolWindow
    private var toolWindowContentsCreated = false

    private val panels = mutableListOf<PackageSearchPanelBase>()
    private val contentFactory = ContentFactory.SERVICE.getInstance()

    private val contentManagerOrNull: ContentManager?
        get() = if (::toolWindow.isInitialized) toolWindow.contentManager else null

    private val contentManagerListener = object : ContentManagerListener {
        override fun selectionChanged(event: ContentManagerEvent) {
            (toolWindow as? ToolWindowEx)?.setAdditionalGearActions(null)
            toolWindow.setTitleActions(emptyList())

            val panel = event.content.component as? HasToolWindowActions
            panel?.gearActions?.let {
                (toolWindow as? ToolWindowEx)?.setAdditionalGearActions(it)
            }
            panel?.titleActions?.let {
                toolWindow.setTitleActions(it.asList())
            }
        }
    }

    fun initialize(toolWindow: ToolWindow) {
        this.toolWindow = toolWindow

        setAvailabilityBasedOnProjectModules()
        startMonitoring()

        project.packageSearchModulesChangesFlow
            .debounce(500)
            .flowOn(Dispatchers.Main)
            .onEach { toolWindow.isAvailable = it.isNotEmpty() }
            .flowOn(Dispatchers.AppUI)
            .launchIn(project.lifecycleScope)
    }

    private fun setAvailabilityBasedOnProjectModules() {
        if (!::toolWindow.isInitialized) return

        if (!toolWindowContentsCreated) {
            createToolWindowContents(toolWindow)
        }
    }

    private fun createToolWindowContents(toolWindow: ToolWindow) {
        toolWindowContentsCreated = true

        toolWindow.title = PackageSearchBundle.message("packagesearch.ui.toolwindow.title")

        val contentManager = checkNotNull(contentManagerOrNull) { "The ContentManager is not available when creating the toolwindow" }

        contentManager.addContentManagerListener(contentManagerListener)
        initializeUi(contentManager)
    }

    private fun initializeUi(contentManager: ContentManager) {
        contentManager.removeAllContents(true)
        panels.clear()

        val rootModel = project.packageSearchDataService
        panels += PackageManagementPanel(
            rootDataModelProvider = rootModel,
            selectedPackageSetter = rootModel,
            targetModuleSetter = rootModel,
            searchClient = rootModel,
            operationExecutor = rootModel
        )

        if (FeatureFlags.showRepositoriesTab) {
            panels += RepositoryManagementPanel(
                rootDataModelProvider = rootModel
            )
        }

        for (panel in panels) {
            initializePanel(panel, contentManager, contentFactory)
        }
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
            content.component.updateAndRepaint()
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
            contentFactory.createContent(null, title, false).apply {
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
                    setAvailabilityBasedOnProjectModules()
                }
            }
        )

        project.messageBus.connect(this).subscribe(
            ProjectTopics.MODULES,
            object : ModuleListener {
                override fun moduleAdded(p: Project, module: Module) {
                    setAvailabilityBasedOnProjectModules()
                }

                override fun moduleRemoved(p: Project, module: Module) {
                    setAvailabilityBasedOnProjectModules()
                }
            }
        )

        val contentManager = checkNotNull(contentManagerOrNull) { "The content manager is unavailable when starting monitoring" }
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener { contentManager.component.updateAndRepaint() })
    }

    override fun dispose() {
        logDebug("PackageSearchToolWindowService#dispose()") { "Disposing PackageSearchToolWindowService..." }
        for (panel in panels) {
            if (panel is Disposable) Disposer.dispose(panel)
        }

        if (::toolWindow.isInitialized && !toolWindow.isDisposed) {
            checkNotNull(contentManagerOrNull) { "The content manager is unavailable when disposing" }
                .removeContentManagerListener(contentManagerListener)
            toolWindow.remove()
        }
    }
}

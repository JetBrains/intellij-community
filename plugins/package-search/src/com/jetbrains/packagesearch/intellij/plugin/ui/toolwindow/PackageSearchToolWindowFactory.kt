package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.castSafelyTo
import com.jetbrains.packagesearch.PackageSearchIcons
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
import com.jetbrains.packagesearch.intellij.plugin.util.addSelectionChangedListener
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logInfo
import com.jetbrains.packagesearch.intellij.plugin.util.lookAndFeelFlow
import com.jetbrains.packagesearch.intellij.plugin.util.map
import com.jetbrains.packagesearch.intellij.plugin.util.onEach
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchDataService
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchModulesChangesFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

class PackageSearchToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {

        private val ToolWindowId = PackageSearchBundle.message("toolwindow.stripe.Dependencies")

        private fun getToolWindow(project: Project) = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId)

        fun activateToolWindow(project: Project, action: () -> Unit) {
            getToolWindow(project)?.activate(action, true, true)
        }
    }

    override fun isApplicable(project: Project): Boolean {
        val isAvailable = runBlocking { project.packageSearchModulesChangesFlow.first().isNotEmpty() }

        if (!isAvailable) project.packageSearchModulesChangesFlow
            .filter { it.isNotEmpty() }
            .take(1)
            .flowOn(Dispatchers.Default)
            .map {
                RegisterToolWindowTask.closable(
                    ToolWindowId,
                    PackageSearchBundle.messagePointer("toolwindow.stripe.Dependencies"),
                    PackageSearchIcons.ArtifactSmall
                )
            }
            .map { toolWindowTask -> ToolWindowManager.getInstance(project).registerToolWindow(toolWindowTask) }
            .onEach { toolWindow -> initialize(toolWindow, project) }
            .flowOn(Dispatchers.AppUI)
            .launchIn(project.lifecycleScope)

        return isAvailable
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) = initialize(toolWindow, project)

    private fun initialize(
        toolWindow: ToolWindow,
        project: Project
    ): Unit = with(toolWindow) {
        title = PackageSearchBundle.message("toolwindow.stripe.Dependencies")

        contentManager.addSelectionChangedListener { event ->
            if (this is ToolWindowEx) {
                setAdditionalGearActions(null)
                event.content.component.castSafelyTo<HasToolWindowActions>()
                    ?.also { setAdditionalGearActions(it.gearActions) }
            }
            setTitleActions(emptyList())
            event.content.component.castSafelyTo<HasToolWindowActions>()
                ?.titleActions
                ?.also { setTitleActions(it.toList()) }
        }

        contentManager.removeAllContents(true)

        val panels = buildList {
            add(
                PackageManagementPanel(
                    rootDataModelProvider = project.packageSearchDataService,
                    selectedPackageSetter = project.packageSearchDataService,
                    targetModuleSetter = project.packageSearchDataService,
                    searchClient = project.packageSearchDataService,
                    operationExecutor = project.packageSearchDataService
                )
            )
            if (FeatureFlags.showRepositoriesTab) {
                add(RepositoryManagementPanel(rootDataModelProvider = project.packageSearchDataService))
            }
        }

        val contentFactory = ContentFactory.SERVICE.getInstance()

        for (panel in panels) {
            panel.initialize(contentManager, contentFactory)
        }

        isAvailable = false

        project.packageSearchModulesChangesFlow
            .map { it.isNotEmpty() }
            .onEach { logInfo("PackageSearchToolWindowFactory#packageSearchModulesChangesFlow") { "Setting toolWindow.isAvailable = $it" } }
            .onEach(Dispatchers.AppUI) { isAvailable = it }
            .launchIn(project.lifecycleScope)

        combine(project.lookAndFeelFlow, project.packageSearchModulesChangesFlow.filter { it.isNotEmpty() }) { _, _ -> }
            .onEach(Dispatchers.AppUI) { contentManager.component.updateAndRepaint() }
            .launchIn(project.lifecycleScope)

    }

    private fun PackageSearchPanelBase.initialize(
        contentManager: ContentManager,
        contentFactory: ContentFactory,
    ) {
        val panelContent = content // should be executed before toolbars
        val toolbar = toolbar
        val topToolbar = topToolbar
        val gearActions = gearActions
        val titleActions = titleActions

        if (topToolbar == null) {
            contentManager.addTab(title, panelContent, toolbar, gearActions, titleActions, contentFactory)
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
                title,
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
        titleActions: Array<AnAction>?,
        contentFactory: ContentFactory
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
}

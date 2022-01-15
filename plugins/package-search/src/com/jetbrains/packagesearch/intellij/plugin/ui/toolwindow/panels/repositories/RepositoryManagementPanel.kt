package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.actions.ShowSettingsAction
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.swing.JScrollPane

internal class RepositoryManagementPanel(
    private val project: Project
) : PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.repositories.title")) {

    private val repositoriesTree = RepositoryTree(project)

    private val autoScrollToSourceHandler = object : AutoScrollToSourceHandler() {
        override fun isAutoScrollMode(): Boolean {
            return PackageSearchGeneralConfiguration.getInstance(project).autoScrollToSource
        }

        override fun setAutoScrollMode(state: Boolean) {
            PackageSearchGeneralConfiguration.getInstance(project).autoScrollToSource = state
        }
    }

    private val mainSplitter =
        JBScrollPane(repositoriesTree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
            border = emptyBorder()
            verticalScrollBar.unitIncrement = 16

            UIUtil.putClientProperty(verticalScrollBar, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, true)
        }

    init {
        project.packageSearchProjectService.allInstalledKnownRepositoriesFlow
            .onEach { repositoriesTree.display(it) }
            .launchIn(project.lifecycleScope)
    }

    override fun build() = mainSplitter

    override fun buildGearActions() = DefaultActionGroup(
        ShowSettingsAction(project),
        autoScrollToSourceHandler.createToggleAction()
    )
}

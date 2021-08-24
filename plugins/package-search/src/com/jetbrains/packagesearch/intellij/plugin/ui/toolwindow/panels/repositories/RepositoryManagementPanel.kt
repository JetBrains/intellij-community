package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.actions.ShowSettingsAction
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RootDataModelProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.util.AppUI
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.swing.JScrollPane

internal class RepositoryManagementPanel(
    private val rootDataModelProvider: RootDataModelProvider
) : PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.repositories.title")) {

    private val repositoriesTree = RepositoryTree(rootDataModelProvider.project)

    private val autoScrollToSourceHandler = object : AutoScrollToSourceHandler() {
        override fun isAutoScrollMode(): Boolean {
            return PackageSearchGeneralConfiguration.getInstance(rootDataModelProvider.project).autoScrollToSource
        }

        override fun setAutoScrollMode(state: Boolean) {
            PackageSearchGeneralConfiguration.getInstance(rootDataModelProvider.project).autoScrollToSource = state
        }
    }

    private val mainSplitter =
        JBScrollPane(repositoriesTree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
            border = emptyBorder()
            verticalScrollBar.unitIncrement = 16

            UIUtil.putClientProperty(verticalScrollBar, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, true)
        }

    init {
        rootDataModelProvider.dataModelFlow
            .map { it.allKnownRepositories }
            .distinctUntilChanged()
            .onEach { repositoriesTree.display(it) }
            .launchIn(rootDataModelProvider.project.lifecycleScope)
    }

    override fun build() = mainSplitter

    override fun buildGearActions() = DefaultActionGroup(
        ShowSettingsAction(rootDataModelProvider.project),
        autoScrollToSourceHandler.createToggleAction()
    )
}

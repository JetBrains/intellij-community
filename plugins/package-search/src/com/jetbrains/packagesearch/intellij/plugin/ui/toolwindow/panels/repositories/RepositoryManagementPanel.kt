package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.actions.ShowSettingsAction
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.LifetimeProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RootDataModelProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.rd.util.reactive.map
import javax.swing.JScrollPane

internal class RepositoryManagementPanel(
    private val rootDataModelProvider: RootDataModelProvider,
    lifetimeProvider: LifetimeProvider
) : PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.repositories.title")) {

    private val repositoriesTree = RepositoryTree(
        project = rootDataModelProvider.project,
        allKnownRepositories = rootDataModelProvider.dataModelProperty.map { it.allKnownRepositories },
        lifetime = lifetimeProvider.lifetime
    )

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

    override fun build() = mainSplitter

    override fun buildGearActions() = DefaultActionGroup(
        ShowSettingsAction(rootDataModelProvider.project),
        autoScrollToSourceHandler.createToggleAction()
    )
}

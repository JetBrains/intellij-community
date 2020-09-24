package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.actions.ShowSettingsAction
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import javax.swing.JComponent
import javax.swing.JScrollPane

class RepositoryManagementPanel(private val viewModel: PackageSearchToolWindowModel) :
    PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.repositories.title")) {

    private val repositoriesTree = RepositoryTree(viewModel)

    private val refreshAction =
        object : AnAction(
            PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.reload.text"),
            PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.reload.description"),
            AllIcons.Actions.Refresh
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                viewModel.requestRefreshContext.fire(true)
            }
        }

    private val autoScrollToSourceHandler = object : AutoScrollToSourceHandler() {
        override fun isAutoScrollMode(): Boolean {
            return PackageSearchGeneralConfiguration.getInstance(viewModel.project).autoScrollToSource
        }

        override fun setAutoScrollMode(state: Boolean) {
            PackageSearchGeneralConfiguration.getInstance(viewModel.project).autoScrollToSource = state
        }
    }

    private val mainSplitter =
        JBScrollPane(repositoriesTree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
            this.border = JBUI.Borders.empty(0, 0, 0, 0)
            this.verticalScrollBar.unitIncrement = 16

            UIUtil.putClientProperty(verticalScrollBar, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, true)
        }

    override fun build() = mainSplitter

    override fun buildToolbar(): JComponent? {
        val actionGroup = DefaultActionGroup(
            refreshAction,
            Separator(),
            ShowSettingsAction(viewModel.project),
            autoScrollToSourceHandler.createToggleAction()
        )

        return ActionManager.getInstance().createActionToolbar("", actionGroup, false).component
    }
}

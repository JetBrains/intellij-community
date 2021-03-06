package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.JBSplitter
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.actions.ShowSettingsAction
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.left.PackagesSmartPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.right.PackagesChosenPanel
import java.awt.Dimension
import javax.swing.JComponent

class PackageManagementPanel(private val viewModel: PackageSearchToolWindowModel) :
    PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.title")) {

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

    private val mainSplitter = JBSplitter("PackageSearch.PackageManagementPanel.Splitter", 0.5f).apply {
        firstComponent = PackagesSmartPanel(viewModel, autoScrollToSourceHandler).content.apply {
            minimumSize = Dimension(JBUI.scale(250), 0)
        }
        secondComponent = PackagesChosenPanel(viewModel).content
        orientation = false
        dividerWidth = JBUI.scale(2)
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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.OperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SelectedPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaledEmptyBorder
import java.awt.CardLayout
import java.awt.Point
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.SwingConstants

internal class PackageDetailsPanel(
    operationFactory: PackageSearchOperationFactory,
    operationExecutor: OperationExecutor
) : PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.selectedPackage")) {

    private var currentPanelName = EMPTY_STATE

    private val cardPanel = PackageSearchUI.cardPanel {
        border = emptyBorder()
    }

    private val headerPanel = PackageDetailsHeaderPanel(operationFactory, operationExecutor)

    private val infoPanel = PackageDetailsInfoPanel()

    private val scrollPanel = PackageSearchUI.verticalScrollPane(infoPanel).apply {
        viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE // https://stackoverflow.com/a/54550638/95901
        UIUtil.putClientProperty(verticalScrollBar, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, true)
    }

    private val emptyStatePanel = PackageSearchUI.borderPanel {
        border = scaledEmptyBorder(12)
        addToCenter(
            PackageSearchUI.createLabel().apply {
                text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.emptyState")
                horizontalAlignment = SwingConstants.CENTER
                foreground = PackageSearchUI.getTextColorSecondary(false)
            }
        )
    }

    init {
        cardPanel.add(emptyStatePanel, EMPTY_STATE)

        val contentPanel = PackageSearchUI.borderPanel {
            border = emptyBorder()
            addToTop(headerPanel)
            addToCenter(scrollPanel)
        }
        cardPanel.add(contentPanel, CONTENT_PANEL)

        showPanel(EMPTY_STATE)
    }

    fun display(
        selectedPackageModel: SelectedPackageModel<*>?,
        knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
        allKnownRepositories: KnownRepositories.All,
        targetModules: TargetModules,
        onlyStable: Boolean
    ) {
        if (selectedPackageModel != null) {
            headerPanel.display(selectedPackageModel, knownRepositoriesInTargetModules, allKnownRepositories, targetModules, onlyStable)
            infoPanel.display(selectedPackageModel.packageModel, selectedPackageModel.selectedVersion, allKnownRepositories)

            showPanel(CONTENT_PANEL)

            AppUIExecutor.onUiThread().later().execute {
                scrollPanel.viewport.viewPosition = Point(0, 0)
            }
        } else {
            showPanel(EMPTY_STATE)
        }
    }

    private fun showPanel(panelName: String) {
        if (currentPanelName == panelName) return
        (cardPanel.layout as CardLayout).show(cardPanel, panelName)
        currentPanelName = panelName
    }

    override fun build(): JPanel = cardPanel

    companion object {

        private const val EMPTY_STATE = "empty_state"
        private const val CONTENT_PANEL = "content_panel"
    }
}

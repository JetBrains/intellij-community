/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RepositoryModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.CardLayout
import java.awt.Point
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.SwingConstants

internal class PackageDetailsPanel(
    project: Project
) : PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.selectedPackage")) {

    private var currentPanelName = EMPTY_STATE

    private val cardPanel = PackageSearchUI.cardPanel {
        border = emptyBorder()
    }

    private val headerPanel = PackageDetailsHeaderPanel(project)

    private val infoPanel = PackageDetailsInfoPanel()

    private val scrollPanel = PackageSearchUI.verticalScrollPane(infoPanel).apply {
        viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE // https://stackoverflow.com/a/54550638/95901
        UIUtil.putClientProperty(verticalScrollBar, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, true)
    }

    private val emptyStatePanel = PackageSearchUI.borderPanel {
        border = emptyBorder(12)
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

    internal data class ViewModel(
        val selectedPackageModel: UiPackageModel<*>?,
        val repositoriesDeclarationsByModule: Map<PackageSearchModule, List<RepositoryModel>>,
        val targetModules: TargetModules,
        val onlyStable: Boolean,
        val invokeLaterScope: CoroutineScope,
        val allKnownRepositories: List<RepositoryModel>
    )

    fun display(viewModel: ViewModel) {
        if (viewModel.selectedPackageModel == null) {
            showPanel(EMPTY_STATE)
            return
        }

        headerPanel.display(
            PackageDetailsHeaderPanel.ViewModel(
                uiPackageModel = viewModel.selectedPackageModel,
                knownRepositoriesInTargetModules = viewModel.repositoriesDeclarationsByModule,
                allKnownRepositories = viewModel.allKnownRepositories,
                targetModules = viewModel.targetModules,
                onlyStable = viewModel.onlyStable
            )
        )
        infoPanel.display(
            PackageDetailsInfoPanel.ViewModel(
                packageModel = viewModel.selectedPackageModel.packageModel,
                selectedVersion = viewModel.selectedPackageModel.selectedVersion.originalVersion,
                allKnownRepositories = viewModel.allKnownRepositories,
                knownRepositoriesInTargetModules = viewModel.repositoriesDeclarationsByModule,
            )
        )

        showPanel(CONTENT_PANEL)

        viewModel.invokeLaterScope.launch(Dispatchers.EDT) { scrollPanel.viewport.viewPosition = Point(0, 0) }
    }

    private fun showPanel(panelName: String) {
        if (currentPanelName == panelName) return
        (cardPanel.layout as CardLayout).show(cardPanel, panelName)
        currentPanelName = panelName
    }

    override fun build(): JPanel = cardPanel
    override fun getData(dataId: String) = null

    companion object {

        private const val EMPTY_STATE = "empty_state"
        private const val CONTENT_PANEL = "content_panel"
    }
}

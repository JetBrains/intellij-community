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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.asSafely
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.actions.PkgsToDAAction
import com.jetbrains.packagesearch.intellij.plugin.actions.ShowSettingsAction
import com.jetbrains.packagesearch.intellij.plugin.actions.TogglePackageDetailsAction
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageIdentifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SearchResultUiState
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.modules.ModulesTree
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails.PackageDetailsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesListPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.computeModuleTreeModel
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.util.combine
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.jetbrains.idea.packagesearch.SortMetric
import java.awt.Dimension
import javax.swing.JScrollPane


@Suppress("MagicNumber") // Swing dimension constants
internal class PackageManagementPanel(private val project: Project) : PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.title")) {

    internal class UIState {

        internal val modulesTree = ModulesTree()
        internal val packagesListPanel = PackagesListPanel()

        internal class ModulesTree {
            internal val targetModulesStateFlow: MutableStateFlow<TargetModules> = MutableStateFlow(TargetModules.None)
        }

        internal class PackagesListPanel {
            internal val onlyMultiplatformStateFlow = MutableStateFlow(false)
            internal val onlyStableStateFlow = MutableStateFlow(false)
            internal val sortMetricStateFlow = MutableStateFlow(SortMetric.NONE)
            internal val searchQueryStateFlow = MutableStateFlow("")
            internal val searchResultsUiStateOverridesState: MutableStateFlow<Map<PackageIdentifier, SearchResultUiState>> =
                MutableStateFlow(emptyMap())

            internal val table = PackagesTable()

            internal class PackagesTable {
                internal val selectedPackageStateFlow = MutableStateFlow<UiPackageModel<*>?>(null)
            }

        }
    }

    internal val modulesTree = ModulesTree(project)

    private val modulesScrollPanel = JBScrollPane(
        modulesTree,
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    )

    internal val packagesListPanel = PackagesListPanel(
        project = project,
        targetModulesFlow = project.service<UIState>().modulesTree.targetModulesStateFlow,
        dataProvider = project.packageSearchProjectService.dataProvider
    )

    private val packageDetailsPanel = PackageDetailsPanel(project)

    private val packagesSplitter = JBSplitter(
        "PackageSearch.PackageManagementPanel.DetailsSplitter",
        PackageSearchGeneralConfiguration.DefaultPackageDetailsSplitterProportion
    ).apply {
        firstComponent = packagesListPanel.content
        secondComponent = packageDetailsPanel.content
        orientation = false // Horizontal split
        dividerWidth = 1.scaled()
        divider.background = PackageSearchUI.Colors.border
    }

    private val mainSplitter = JBSplitter("PackageSearch.PackageManagementPanel.Splitter", 0.1f).apply {
        firstComponent = modulesScrollPanel
        secondComponent = packagesSplitter
        orientation = false // Horizontal split
        dividerWidth = 1.scaled()
        divider.background = PackageSearchUI.Colors.border
    }

    init {
        updatePackageDetailsVisible(PackageSearchGeneralConfiguration.getInstance(project).packageDetailsVisible)

        modulesScrollPanel.apply {
            border = emptyBorder()
            minimumSize = Dimension(250.scaled(), 0)

            UIUtil.putClientProperty(verticalScrollBar, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, true)
        }

        packagesListPanel.content.minimumSize = Dimension(250.scaled(), 0)

        project.packageSearchProjectService.packageSearchModulesStateFlow
            .map { computeModuleTreeModel(it) }
            .onEach { modulesTree.display(it) }
            .flowOn(Dispatchers.EDT)
            .launchIn(project.lifecycleScope)

        project.service<UIState>()
            .packagesListPanel
            .table
            .selectedPackageStateFlow
            .filterNotNull()
            .onEach { PackageSearchEventsLogger.logPackageSelected(it is UiPackageModel.Installed) }
            .launchIn(project.lifecycleScope)

        project.service<UIState>()
            .modulesTree
            .targetModulesStateFlow
            .onEach { PackageSearchEventsLogger.logTargetModuleSelected(it) }
            .launchIn(project.lifecycleScope)

        combine(
            project.packageSearchProjectService.allKnownRepositoriesFlow,
            project.packageSearchProjectService.repositoriesDeclarationsByModuleFlow,
            project.service<UIState>().packagesListPanel.table.selectedPackageStateFlow,
            project.service<UIState>().modulesTree.targetModulesStateFlow,
            project.service<UIState>().packagesListPanel.onlyStableStateFlow,
            project.service<UIState>().packagesListPanel.sortMetricStateFlow,
        ) { allKnownRepositories, repositoriesDeclarationsByModule,
            selectedUiPackageModel, targetModules, onlyStable, sortMetric ->
            PackageDetailsPanel.ViewModel(
                selectedPackageModel = selectedUiPackageModel,
                repositoriesDeclarationsByModule = repositoriesDeclarationsByModule,
                allKnownRepositories = allKnownRepositories,
                targetModules = targetModules,
                onlyStable = onlyStable,
                sortMetric = sortMetric,
                invokeLaterScope = project.lifecycleScope
            )
        }
            .onEach { packageDetailsPanel.display(it) }
            .flowOn(Dispatchers.EDT)
            .launchIn(project.lifecycleScope)
    }

    private fun updatePackageDetailsVisible(becomeVisible: Boolean) {
        val wasVisible = packagesSplitter.secondComponent.isVisible
        packagesSplitter.secondComponent.isVisible = becomeVisible

        if (!wasVisible && becomeVisible) {
            packagesSplitter.proportion =
                PackageSearchGeneralConfiguration.getInstance(project).packageDetailsSplitterProportion
        }

        if (!becomeVisible) {
            PackageSearchGeneralConfiguration.getInstance(project).packageDetailsSplitterProportion =
                packagesSplitter.proportion
            packagesSplitter.proportion = 1.0f
        }
    }

    private val togglePackageDetailsAction = TogglePackageDetailsAction(project, ::updatePackageDetailsVisible)

    override fun build() = mainSplitter

    override fun buildGearActions() = DefaultActionGroup(
        ShowSettingsAction(project),
        togglePackageDetailsAction
    )

    override fun buildTitleActions(): List<AnAction> = listOf(togglePackageDetailsAction)

    override fun getData(dataId: String): PackageModel.Installed? {
        return when {
            PkgsToDAAction.PACKAGES_LIST_PANEL_DATA_KEY.`is`(dataId) -> project.service<UIState>()
                .packagesListPanel
                .table
                .selectedPackageStateFlow
                .value
                ?.packageModel
                ?.asSafely<PackageModel.Installed>()
            else -> null
        }
    }
}

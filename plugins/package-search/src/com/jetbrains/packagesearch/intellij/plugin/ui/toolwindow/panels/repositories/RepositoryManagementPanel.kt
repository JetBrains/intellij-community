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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.actions.ShowSettingsAction
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.util.combineLatest
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
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
        combineLatest(
            project.packageSearchProjectService.repositoriesDeclarationsByModuleFlow,
            project.packageSearchProjectService.allKnownRepositoriesFlow
        ) { repositoriesDeclarationsByModule, allKnownRepositories ->
            repositoriesTree.display(repositoriesDeclarationsByModule, allKnownRepositories)
        }
            .flowOn(Dispatchers.EDT)
            .launchIn(project.lifecycleScope)
    }

    override fun build() = mainSplitter

    override fun buildGearActions() = DefaultActionGroup(
        ShowSettingsAction(project),
        autoScrollToSourceHandler.createToggleAction()
    )

    override fun getData(dataId: String) = null
}

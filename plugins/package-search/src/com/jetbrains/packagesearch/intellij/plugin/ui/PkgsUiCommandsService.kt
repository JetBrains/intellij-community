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

package com.jetbrains.packagesearch.intellij.plugin.ui

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.PackagesListPanelProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateModifier
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

internal class PkgsUiCommandsService(private val project: Project) : UiStateModifier {

    private val modulesTree
        get() = project.service<PackagesListPanelProvider.PanelContainer>()
            .panel
            .modulesTree

    private val packagesListPanel
        get() = project.service<PackagesListPanelProvider.PanelContainer>()
            .panel
            .packagesListPanel

    private val packagesTable
        get() = project.service<PackagesListPanelProvider.PanelContainer>()
            .panel
            .packagesListPanel
            .packagesTable

    override fun setSearchQuery(query: String) {
        project.lifecycleScope.launch(Dispatchers.EDT) {
            packagesListPanel.searchTextField.text = query
        }
    }

    override fun setTargetModules(modules: TargetModules) {
        project.lifecycleScope.launch {
            val queue = mutableListOf(modulesTree.model.root as DefaultMutableTreeNode)
            var path: TreePath? = null
            while (queue.isNotEmpty()) {
                val elem = queue.removeAt(0)
                if (elem.userObject as TargetModules == modules) {
                    path = TreePath(elem.path)
                    break
                } else {
                    queue.addAll(elem.children().asSequence().filterIsInstance<DefaultMutableTreeNode>())
                }
            }
            if (path != null) withContext(Dispatchers.EDT) {
                modulesTree.selectionModel.selectionPath = path
            }
        }
    }

    override fun setDependency(coordinates: UnifiedDependency) {
        project.lifecycleScope.launch {
            packagesTable.setSelection(coordinates)
        }
    }
}

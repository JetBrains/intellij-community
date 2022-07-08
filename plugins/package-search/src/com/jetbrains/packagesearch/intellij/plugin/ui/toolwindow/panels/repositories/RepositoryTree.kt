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

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.util.Displayable
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

internal class RepositoryTree(
    private val project: Project
) : Tree(), DataProvider, CopyProvider, Displayable<KnownRepositories.All> {

    private val rootNode: DefaultMutableTreeNode
        get() = (model as DefaultTreeModel).root as DefaultMutableTreeNode

    init {
        setCellRenderer(RepositoryTreeItemRenderer())
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        rootVisible = false
        isRootVisible = false
        showsRootHandles = true

        @Suppress("MagicNumber") // Swing dimension constants
        border = emptyBorder(left = 8)
        emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.repositories.no.repositories.configured")

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                if (e != null && e.clickCount >= 1) {
                    val treePath = getPathForLocation(e.x, e.y) ?: return
                    val item = getRepositoryItemFrom(treePath)
                    if (item != null && item is RepositoryTreeItem.Module) {
                        openFile(item)
                    }
                }
            }
        })

        addTreeSelectionListener {
            val item = getRepositoryItemFrom(it.newLeadSelectionPath)
            if (item != null && item is RepositoryTreeItem.Module) {
                openFile(item)
            }
        }

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                if (e?.keyCode == KeyEvent.VK_ENTER) {
                    val item = getRepositoryItemFrom(selectionPath)
                    if (item != null && item is RepositoryTreeItem.Module) {
                        openFile(item)
                    }
                }
            }
        })

        TreeUIHelper.getInstance().installTreeSpeedSearch(this)

        TreeUtil.installActions(this)
    }

    private fun openFile(repositoryModuleItem: RepositoryTreeItem.Module, focusEditor: Boolean = false) {
        if (!PackageSearchGeneralConfiguration.getInstance(project).autoScrollToSource) return

        val file = repositoryModuleItem.usageInfo.projectModule.buildFile ?: return
        FileEditorManager.getInstance(project).openFile(file, focusEditor, true)
    }

    override suspend fun display(viewModel: KnownRepositories.All) = withContext(Dispatchers.EDT) {
        val previouslySelectedItem = getSelectedRepositoryItem()

        clearSelection()
        rootNode.removeAllChildren()

        val sortedRepositories = viewModel.sortedBy { it.displayName }
        for (repository in sortedRepositories) {
            if (repository.usageInfo.isEmpty()) continue

            val repoItem = RepositoryTreeItem.Repository(repository)
            val repoNode = DefaultMutableTreeNode(repoItem)

            for (usageInfo in repository.usageInfo) {
                val moduleItem = RepositoryTreeItem.Module(usageInfo)
                val treeNode = DefaultMutableTreeNode(moduleItem)
                repoNode.add(treeNode)

                if (previouslySelectedItem == moduleItem) {
                    selectionModel.selectionPath = TreePath(treeNode)
                }
            }

            rootNode.add(repoNode)

            if (previouslySelectedItem == repoItem) {
                selectionModel.selectionPath = TreePath(repoNode)
            }
        }

        TreeUtil.expandAll(this@RepositoryTree)
        updateUI()
    }

    override fun getData(dataId: String) =
        when (val selectedItem = getSelectedRepositoryItem()) {
            is DataProvider -> selectedItem.getData(dataId)
            else -> null
        }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun performCopy(dataContext: DataContext) {
        val selectedItem = getSelectedRepositoryItem()
        if (selectedItem is CopyProvider) selectedItem.performCopy(dataContext)
    }

    override fun isCopyEnabled(dataContext: DataContext): Boolean {
        val selectedItem = getSelectedRepositoryItem()
        return selectedItem is CopyProvider && selectedItem.isCopyEnabled(dataContext)
    }

    override fun isCopyVisible(dataContext: DataContext): Boolean {
        val selectedItem = getSelectedRepositoryItem()
        return selectedItem is CopyProvider && selectedItem.isCopyVisible(dataContext)
    }

    private fun getSelectedRepositoryItem() = getRepositoryItemFrom(this.selectionPath)

    private fun getRepositoryItemFrom(treePath: TreePath?): RepositoryTreeItem? {
        val item = treePath?.lastPathComponent as? DefaultMutableTreeNode?
        return item?.userObject as? RepositoryTreeItem
    }
}

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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.modules

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.asSafely
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.PackageManagementPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.findPathWithData
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

internal class ModulesTree(private val project: Project) : Tree(DefaultMutableTreeNode(TargetModules.None)), DataProvider, CopyProvider {

    init {
        addTreeSelectionListener {
            val node = lastSelectedPathComponent as DefaultMutableTreeNode?
            if (node == null) {
                setTargetModules(TargetModules.None, TraceInfo(TraceInfo.TraceSource.TARGET_MODULES_SELECTION_CHANGE))
                return@addTreeSelectionListener
            }

            val targetModules = checkNotNull(node.userObject as? TargetModules) {
                "Node '${node.path}' has invalid data: ${node.userObject}"
            }

            setTargetModules(targetModules, TraceInfo(TraceInfo.TraceSource.TARGET_MODULES_SELECTION_CHANGE))
        }

        setCellRenderer(ModulesTreeItemRenderer())
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        showsRootHandles = true
        emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.modulesTree.empty")

        @Suppress("MagicNumber") // Swing dimension constants
        border = emptyBorder(left = 8)

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                if (e?.keyCode == KeyEvent.VK_ENTER) {
                    val item = getTargetModulesFrom(selectionPath)
                    setTargetModules(item, TraceInfo(TraceInfo.TraceSource.TARGET_MODULES_KEYPRESS))
                }
            }
        })

        TreeUIHelper.getInstance().installTreeSpeedSearch(this)
        TreeUtil.installActions(this)
    }

    fun display(treeModel: TreeModel) {
        if (treeModel == model) return
        setPaintBusy(true)
        val wasEmpty = model.root == null || model.getChildCount(model.root) == 0
        val lastSelected = selectionPath
            ?.path
            ?.filterIsInstance<DefaultMutableTreeNode>()
            ?.mapNotNull { it.userObject as? TargetModules }
            ?.map {
                when(it) {
                    is TargetModules.All -> "root"
                    is TargetModules.One -> it.module.name
                    TargetModules.None -> ""
                }
            }
            ?: emptyList()


        // Swapping model resets the selection — but, we set the right selection just afterwards
        model = treeModel
        expandAll()
        selectionPath = model.root.asSafely<DefaultMutableTreeNode>()!!.findPathWithData(lastSelected)
        updateUI()
        setPaintBusy(false)
    }

    private fun getTargetModulesFrom(treePath: TreePath?): TargetModules {
        val item = treePath?.lastPathComponent as? DefaultMutableTreeNode?
        return item?.userObject as? TargetModules ?: TargetModules.None
    }

    private fun setTargetModules(targetModules: TargetModules, traceInfo: TraceInfo?) {
        logDebug(traceInfo, "ModulesTree#setTargetModules()") { "Target module changed, now it's $targetModules" }
        project.lifecycleScope.launch { project.service<PackageManagementPanel.UIState>().modulesTree.targetModulesStateFlow.emit(targetModules) }
    }

    override fun getData(dataId: String): Any? = when {
        PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> this
        else -> null
    }

    override fun performCopy(dataContext: DataContext) {
        val dataToCopy = project.service<PackageManagementPanel.UIState>()
            .modulesTree
            .targetModulesStateFlow
            .value
            .takeIf { it !is TargetModules.None }
            ?.modules
            ?.joinToString { it.getFullName() }
            ?: return

        CopyPasteManager.getInstance().setContents(StringSelection(dataToCopy))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isCopyEnabled(dataContext: DataContext) = true

    override fun isCopyVisible(dataContext: DataContext) = true

    private fun expandAll() = TreeUtil.expandAll(this)
}



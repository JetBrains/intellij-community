package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.modules

import com.intellij.ide.CopyProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModuleSetter
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.util.AppUI
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaledEmptyBorder
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.concurrent.CancellationException
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

internal class ModulesTree(
    private val targetModuleSetter: TargetModuleSetter
) : Tree(DefaultMutableTreeNode(TargetModules.None)), DataProvider, CopyProvider, Disposable, CoroutineScope {

    private var latestTargetModules: TargetModules? = null
    private var ignoreTargetModulesChanges = false

    override val coroutineContext = SupervisorJob() + CoroutineName("ModulesTree")

    init {
        setCellRenderer(ModulesTreeItemRenderer())
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        showsRootHandles = true
        emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.modulesTree.empty")

        @Suppress("MagicNumber") // Swing dimension constants
        border = scaledEmptyBorder(left = 8)

        addTreeSelectionListener { event ->
            val item = getTargetModulesFrom(event.newLeadSelectionPath)
            setTargetModules(item, TraceInfo(TraceInfo.TraceSource.TARGET_MODULES_SELECTION_CHANGE))
        }

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

        setTargetModules(TargetModules.None, traceInfo = TraceInfo.EMPTY)
    }

    fun display(projectModules: List<ModuleModel>, targetModules: TargetModules, traceInfo: TraceInfo) = launch {
        logDebug(traceInfo, "ModulesTree#display()") { "Got ${projectModules.size} modules" }
        launch(Dispatchers.AppUI) { setPaintBusy(true) }

        val (treeModel, pendingSelectionPath) = if (projectModules.isEmpty()) {
            logDebug(traceInfo, "ModulesTree#display()") { "No modules to display, setting target to None" }
            DefaultTreeModel(DefaultMutableTreeNode(TargetModules.None)) to null
        } else {
            logDebug(traceInfo, "ModulesTree#display()") { "Populating tree" }
            createTreeModelFrom(projectModules, TargetModules.all(projectModules), targetModules)
        }

        val modelChanged = !areTreesIdentical(model.root as DefaultMutableTreeNode, treeModel.root as DefaultMutableTreeNode)

        withContext(Dispatchers.AppUI) {
            logDebug(traceInfo, "ModulesTree#display()") { "Tree populated. Found selection path: '$pendingSelectionPath'" }
            if (modelChanged) {
                logDebug(traceInfo, "ModulesTree#display()") { "Tree changes detected, yay" }

                // Swapping model resets the selection — but we set the right selection just afterwards
                ignoreTargetModulesChanges = true
                model = treeModel
                ignoreTargetModulesChanges = false

                selectionModel.selectionPath = pendingSelectionPath ?: TreePath(treeModel.root)

                TreeUtil.expandAll(this@ModulesTree)
                updateUI()
            } else {
                logDebug(traceInfo, "ModulesTree#display()") { "No tree changes detected, ignoring" }
            }
            setPaintBusy(false)
        }
    }

    private suspend fun createTreeModelFrom(
        modules: List<ModuleModel>,
        rootTargetModules: TargetModules.All,
        currentTargetModules: TargetModules
    ): Pair<TreeModel, TreePath?> {
        val (rootNode, selectionPath) = withContext(Dispatchers.Default) {
            val sortedModules = modules.sortedBy { it.projectModule.name }
                .toMutableList()

            DefaultMutableTreeNode(rootTargetModules)
                .appendChildren(sortedModules, currentTargetModules)
        }
        return DefaultTreeModel(rootNode) to selectionPath
    }

    private fun DefaultMutableTreeNode.appendChildren(
        sortedModules: List<ModuleModel>,
        currentTargetModules: TargetModules
    ): Pair<TreeNode, TreePath?> {
        val childModules = when (val nodeTargetModules = userObject as TargetModules) {
            is TargetModules.None -> emptyList()
            is TargetModules.One -> {
                sortedModules.filter { nodeTargetModules.module.projectModule == it.projectModule.parent }
            }
            is TargetModules.All -> {
                sortedModules.filter { module -> module.projectModule.parent == null }
            }
        }

        var selectionPath: TreePath? = null
        for (childModule in childModules) {
            val nodeTargetModules = TargetModules.One(childModule)
            val childNode = DefaultMutableTreeNode(nodeTargetModules)
            add(childNode)

            if (nodeTargetModules == currentTargetModules) {
                selectionPath = TreePath(childNode)
            }

            childNode.appendChildren(sortedModules, currentTargetModules)
        }

        return this to selectionPath
    }

    private fun getTargetModulesFrom(treePath: TreePath?): TargetModules {
        val item = treePath?.lastPathComponent as? DefaultMutableTreeNode?
        return item?.userObject as? TargetModules ?: TargetModules.None
    }

    private fun setTargetModules(targetModules: TargetModules, traceInfo: TraceInfo?) {
        if (ignoreTargetModulesChanges) {
            logDebug(traceInfo, "ModulesTree#setTargetModules()") { "Ignoring target module change (ignoreTargetModulesChanges)" }
            return
        }

        if (targetModules == latestTargetModules) {
            logDebug(traceInfo, "ModulesTree#setTargetModules()") { "Ignoring target module change (no actual changes)" }
            return
        }

        logDebug(traceInfo, "ModulesTree#setTargetModules()") { "Target module changed, now it's $targetModules" }
        latestTargetModules = targetModules
        targetModuleSetter.setTargetModules(targetModules)
    }

    private fun areTreesIdentical(first: DefaultMutableTreeNode, second: DefaultMutableTreeNode): Boolean {
        if (first.userObject != second.userObject) return false
        if (first.childCount != second.childCount) return false

        val firstChildren = first.children().toList()
        val otherChildren = second.children().toList()
        for (i in firstChildren.indices) {
            if (!areTreesIdentical(firstChildren[i] as DefaultMutableTreeNode, otherChildren[i] as DefaultMutableTreeNode)) {
                return false
            }
        }
        return true
    }

    override fun getData(dataId: String): Any? = when {
        PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> this
        else -> null
    }

    override fun performCopy(dataContext: DataContext) {
        val dataToCopy = latestTargetModules?.joinToString { it.projectModule.getFullName() }
            ?: return

        CopyPasteManager.getInstance().setContents(StringSelection(dataToCopy))
    }

    override fun isCopyEnabled(dataContext: DataContext) = true

    override fun isCopyVisible(dataContext: DataContext) = true

    override fun dispose() {
        logDebug("ModulesTree#dispose()") { "Disposing ModulesTree..." }
        coroutineContext.cancel(CancellationException("Disposing"))
    }
}

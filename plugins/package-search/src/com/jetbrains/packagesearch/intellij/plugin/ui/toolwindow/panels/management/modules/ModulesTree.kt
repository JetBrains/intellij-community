package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.modules

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModuleSetter
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.util.Displayable
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaledEmptyBorder
import com.jetbrains.packagesearch.intellij.plugin.util.AppUI
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

internal class ModulesTree(
    private val targetModuleSetter: TargetModuleSetter
) : Tree(DefaultMutableTreeNode(TargetModules.None)), DataProvider, CopyProvider, Displayable<ModulesTree.ViewModel> {

    private var latestTargetModules: TargetModules? = null

    private val onTreeItemSelected = TreeSelectionListener {
        val node = lastSelectedPathComponent as DefaultMutableTreeNode?
        if (node == null) {
            setTargetModules(TargetModules.None, TraceInfo(TraceInfo.TraceSource.TARGET_MODULES_SELECTION_CHANGE))
            return@TreeSelectionListener
        }

        val targetModules = checkNotNull(node.userObject as? TargetModules) {
            "Node '${node.path}' has invalid data: ${node.userObject}"
        }
        PackageSearchEventsLogger.logModuleSelected(targetModules::class.simpleName)

        setTargetModules(targetModules, TraceInfo(TraceInfo.TraceSource.TARGET_MODULES_SELECTION_CHANGE))
    }

    init {
        setCellRenderer(ModulesTreeItemRenderer())
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        showsRootHandles = true
        emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.modulesTree.empty")

        @Suppress("MagicNumber") // Swing dimension constants
        border = scaledEmptyBorder(left = 8)

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

    internal data class ViewModel(
        val treeModel: TreeModel,
        val pendingSelectionPath: TreePath,
        val traceInfo: TraceInfo
    )

    override suspend fun display(viewModel: ViewModel) = withContext(Dispatchers.AppUI) {
        if (model.root == null || model.getChildCount(model.root) == 0)
            targetModuleSetter.setTargetModules(getTargetModulesFrom(viewModel.pendingSelectionPath))

        setPaintBusy(true)

        logDebug(viewModel.traceInfo, "ModulesTree#display()") { "Tree populated. Found selection path: '${viewModel.pendingSelectionPath}'" }

        // Swapping model resets the selection — but, we set the right selection just afterwards
        removeTreeSelectionListener(onTreeItemSelected)
        model = viewModel.treeModel
        selectionModel.selectionPath = viewModel.pendingSelectionPath
        addTreeSelectionListener(onTreeItemSelected)

        TreeUtil.expandAll(this@ModulesTree)
        updateUI()
        setPaintBusy(false)
    }

    private fun getTargetModulesFrom(treePath: TreePath?): TargetModules {
        val item = treePath?.lastPathComponent as? DefaultMutableTreeNode?
        return item?.userObject as? TargetModules ?: TargetModules.None
    }

    private fun setTargetModules(targetModules: TargetModules, traceInfo: TraceInfo?) {
        logDebug(traceInfo, "ModulesTree#setTargetModules()") { "Target module changed, now it's $targetModules" }
        latestTargetModules = targetModules
        targetModuleSetter.setTargetModules(targetModules)
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
}

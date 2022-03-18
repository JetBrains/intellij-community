package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.modules

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.uiStateSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

internal class ModulesTree(
    project: Project
) : Tree(DefaultMutableTreeNode(TargetModules.None)), DataProvider, CopyProvider {

    private val targetModulesChannel = Channel<TargetModules>(onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val targetModulesFlow = targetModulesChannel.consumeAsFlow()
        .stateIn(project.lifecycleScope, SharingStarted.Eagerly, TargetModules.None)

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
            PackageSearchEventsLogger.logTargetModuleSelected(targetModules)

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
        project.uiStateSource.targetModulesFlow
            .mapNotNull { selectedTargetModule ->
                val queue = mutableListOf(model.root as DefaultMutableTreeNode)
                while (queue.isNotEmpty()) {
                    val elem = queue.removeAt(0)
                    if (elem.userObject as TargetModules == selectedTargetModule) {
                        return@mapNotNull TreePath(elem.path)
                    } else {
                        queue.addAll(elem.children().asSequence().filterIsInstance<DefaultMutableTreeNode>())
                    }
                }
                null
            }
            .flowOn(Dispatchers.Default)
            .onEach { selectionModel.selectionPath = it }
            .flowOn(Dispatchers.EDT)
            .launchIn(project.lifecycleScope)
    }

    fun display(treeModel: TreeModel) {
        setPaintBusy(true)
        val wasEmpty = model.root == null || model.getChildCount(model.root) == 0
        // Swapping model resets the selection — but, we set the right selection just afterwards
        model = treeModel
        if (wasEmpty) TreeUtil.expandAll(this@ModulesTree)

        updateUI()
        setPaintBusy(false)
    }

    private fun getTargetModulesFrom(treePath: TreePath?): TargetModules {
        val item = treePath?.lastPathComponent as? DefaultMutableTreeNode?
        return item?.userObject as? TargetModules ?: TargetModules.None
    }

    private fun setTargetModules(targetModules: TargetModules, traceInfo: TraceInfo?) {
        logDebug(traceInfo, "ModulesTree#setTargetModules()") { "Target module changed, now it's $targetModules" }
        targetModulesChannel.trySend(targetModules)
    }

    override fun getData(dataId: String): Any? = when {
        PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> this
        else -> null
    }

    override fun performCopy(dataContext: DataContext) {
        val dataToCopy = targetModulesFlow.value.takeIf { it !is TargetModules.None }?.joinToString { it.projectModule.getFullName() }
            ?: return

        CopyPasteManager.getInstance().setContents(StringSelection(dataToCopy))
    }

    override fun isCopyEnabled(dataContext: DataContext) = true

    override fun isCopyVisible(dataContext: DataContext) = true
}

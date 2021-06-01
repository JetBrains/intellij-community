package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaledEmptyBorder
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IPropertyView
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

internal class RepositoryTree(
    private val project: Project,
    allKnownRepositories: IPropertyView<KnownRepositories.All>,
    lifetime: Lifetime
) : Tree(), DataProvider, CopyProvider {

    private val rootNode: DefaultMutableTreeNode
        get() = (model as DefaultTreeModel).root as DefaultMutableTreeNode

    init {
        setCellRenderer(RepositoryTreeItemRenderer())
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        rootVisible = false
        isRootVisible = false
        showsRootHandles = true

        @Suppress("MagicNumber") // Swing dimension constants
        border = scaledEmptyBorder(left = 8)
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

        allKnownRepositories.advise(lifetime) { repositories -> onRepositoriesChanged(repositories) }
    }

    private fun openFile(repositoryModuleItem: RepositoryTreeItem.Module, focusEditor: Boolean = false) {
        if (!PackageSearchGeneralConfiguration.getInstance(project).autoScrollToSource) return

        val file = repositoryModuleItem.usageInfo.projectModule.buildFile
        FileEditorManager.getInstance(project).openFile(file, focusEditor, true)
    }

    private fun onRepositoriesChanged(repositories: KnownRepositories.All) {
        val previouslySelectedItem = getSelectedRepositoryItem()

        clearSelection()
        rootNode.removeAllChildren()

        val sortedRepositories = repositories.sortedBy { it.displayName }
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

        TreeUtil.expandAll(this)
        updateUI()
    }

    override fun getData(dataId: String) =
        when (val selectedItem = getSelectedRepositoryItem()) {
            is DataProvider -> selectedItem.getData(dataId)
            else -> null
        }

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

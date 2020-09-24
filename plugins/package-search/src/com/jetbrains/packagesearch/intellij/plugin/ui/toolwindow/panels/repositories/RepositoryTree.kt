package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.localizedName
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class RepositoryTree(val viewModel: PackageSearchToolWindowModel) :
    Tree(), DataProvider, CopyProvider {

    val rootNode: DefaultMutableTreeNode
        get() = (model as DefaultTreeModel).root as DefaultMutableTreeNode

    init {

        setCellRenderer(RepositoryTreeItemRenderer())
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        rootVisible = false
        isRootVisible = false
        showsRootHandles = true

        @Suppress("MagicNumber") // Gotta love Swing APIs
        border = JBUI.Borders.empty(0, 8, 0, 0)
        emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.repositories.no.repositories.configured")

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                if (e != null && e.clickCount >= 1) {
                    val treePath = getPathForLocation(e.x, e.y) ?: return
                    val item = getRepositoryItemFrom(treePath)
                    if (item != null && item is RepositoryItem.Module) {
                        openFile(item)
                    }
                }
            }
        })

        addTreeSelectionListener {
            val item = getRepositoryItemFrom(it.newLeadSelectionPath)
            if (item != null && item is RepositoryItem.Module) {
                openFile(item)
            }
        }

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                if (e?.keyCode == KeyEvent.VK_ENTER) {
                    val item = getRepositoryItemFrom(selectionPath)
                    if (item != null && item is RepositoryItem.Module) {
                        openFile(item)
                    }
                }
            }
        })

        TreeUtil.installActions(this)

        TreeSpeedSearch(this) {
            buildString {
                val item = getRepositoryItemFrom(selectionPath)
                if (item != null) {
                    item.meta.remoteInfo?.let { append(it.localizedName()) }
                    item.meta.name?.let { append(it) }

                    when (item) {
                        is RepositoryItem.Group -> {
                            item.meta.url?.let { append(it) }
                        }
                        is RepositoryItem.Module -> {
                            append(item.meta.projectModule?.getFullName())
                        }
                    }
                }
            }
        }.apply {
            comparator = SpeedSearchComparator(false)
        }

        viewModel.repositories.advise(viewModel.lifetime) { packageSearchRepositories ->
            val previouslySelectedIdentifier = getSelectedRepositoryItem()?.toSimpleIdentifier()

            clearSelection()
            rootNode.removeAllChildren()

            for (group in packageSearchRepositories
                .sortedBy { it.remoteInfo?.localizedName() ?: it.name ?: it.id }
                .sortedBy { it.projectModule?.getFullName() ?: "" }
                .groupBy { it.remoteInfo?.localizedName() + (it.id ?: it.name ?: "") + (it.url ?: "") }) {

                val groupItem = RepositoryItem.Group(group.value.first())
                val groupNode = DefaultMutableTreeNode(groupItem)
                rootNode.add(groupNode)

                for (packageSearchRepository in group.value) {
                    val repositoryItem = RepositoryItem.Module(packageSearchRepository)
                    val treeNode = DefaultMutableTreeNode(repositoryItem)
                    groupNode.add(treeNode)

                    if (previouslySelectedIdentifier != null && previouslySelectedIdentifier == repositoryItem.toSimpleIdentifier()) {
                        selectionModel.selectionPath = TreePath(treeNode)
                    }
                }
            }

            TreeUtil.expandAll(this)
            updateUI()
        }
    }

    private fun getRepositoryItemFrom(treePath: TreePath?): RepositoryItem? {
        val item = treePath?.lastPathComponent as? DefaultMutableTreeNode?
        return item?.userObject as? RepositoryItem
    }

    private fun getSelectedRepositoryItem() = getRepositoryItemFrom(this.selectionPath)

    override fun getData(dataId: String) = getSelectedRepositoryItem()?.getData(dataId)

    override fun performCopy(dataContext: DataContext) {
        getSelectedRepositoryItem()?.performCopy(dataContext)
    }

    override fun isCopyEnabled(dataContext: DataContext) = getSelectedRepositoryItem()?.isCopyEnabled(dataContext) ?: false
    override fun isCopyVisible(dataContext: DataContext) = getSelectedRepositoryItem()?.isCopyVisible(dataContext) ?: false

    private fun openFile(repositoryModuleItem: RepositoryItem.Module, focusEditor: Boolean = false) {
        if (!PackageSearchGeneralConfiguration.getInstance(viewModel.project).autoScrollToSource) return

        repositoryModuleItem.meta.projectModule?.buildFile?.let { file ->

            // TODO At some point it would be nice to jump to the location in the file
            FileEditorManager.getInstance(viewModel.project).openFile(file, focusEditor, true)
        }
    }
}

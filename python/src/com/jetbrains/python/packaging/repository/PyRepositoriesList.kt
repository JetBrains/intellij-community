// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.TreePath

@ApiStatus.Experimental
class PyRepositoriesList(val project: Project) : MasterDetailsComponent() {

  private val defaultRepositoryNodes = mutableListOf<PyRepoNode>()

  private val invalidRepos get() = service<PyPackageRepositories>().invalidRepositories
  private val presenter = PyRepositoriesPresenter(service<PyPackageRepositories>())

  init {
    initTree()

    presenter.loadCustomRepositories()
      .map { PyRepoNode(PyRepositoryListItem(it, project, getAllNames = ::allRepoNames)) }
      .forEach { addNode(it, myRoot) }

    addDefaultRepositories()
  }

  override fun initTree() {
    super.initTree()
    myTree.showsRootHandles = false
    (myTree.ui as BasicTreeUI).apply {
      leftChildIndent = 0
      rightChildIndent = 0
    }

    val errorIcon = AllIcons.General.Error

    SwingUtilities.invokeLater {
      val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, myTree) as JScrollPane?
      scrollPane?.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    myTree.setCellRenderer(object : MyColoredTreeCellRenderer() {
      private var paintError = false

      init {
        ipad = JBUI.insetsLeft(4)
      }

      override fun getPreferredSize(): Dimension {
        val pref = super.getPreferredSize()
        val vpWidth = (myTree.parent as? JViewport)?.width ?: 0
        return if (vpWidth > pref.width) Dimension(vpWidth, pref.height) else pref
      }

      override fun getAdditionalAttributes(node: MyNode): SimpleTextAttributes {
        if (node.repoItem.repository in invalidRepos) {
          return SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.RED)
        }
        return super.getAdditionalAttributes(node)
      }

      override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
        super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
        paintError = (value as PyRepoNode).item.repository in invalidRepos
        if (paintError) {
          append(" " + PyBundle.message("python.packaging.repository.tree.connection.failed"),
                 SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.RED))
        }
      }

      override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (paintError) {
          val iconX = width - errorIcon.iconWidth - UIUtil.getScrollBarWidth()
          val iconY = (height - errorIcon.iconHeight) / 2
          errorIcon.paintIcon(this, g, iconX, iconY)
        }
      }
    })
  }

  private fun addDefaultRepositories() {
    for (repo in presenter.loadBuiltInRepositories(project)) {
      val node = PyRepoNode(PyRepositoryListItem(repo, project, isDefault = true))
      addNode(node, myRoot)
      defaultRepositoryNodes.add(node)
    }
  }

  override fun createComponent(): JComponent {
    val component = super.createComponent()
    val splitter = myWholePanel?.components?.filterIsInstance<Splitter>()?.firstOrNull()
    if (splitter != null) {
      splitter.dividerWidth = 1
      splitter.divider.background = splitter.background

      val leftPanel = splitter.firstComponent as JPanel
      leftPanel.border = JBUI.Borders.compound(
        JBUI.Borders.emptyRight(UIUtil.getRegularPanelInsets().right),
        IdeBorderFactory.createRoundedBorder()
      )

      removeActionsPanelBorder(leftPanel)
    }
    return component
  }

  private fun removeActionsPanelBorder(container: JComponent) {
    for (child in container.components) {
      if (child is CommonActionsPanel) {
        child.border = JBUI.Borders.empty()
      }
      else if (child is JComponent) {
        removeActionsPanelBorder(child)
      }
    }
  }

  override fun createActions(fromPopup: Boolean): List<AnAction> {
    val add = object : DumbAwareAction({ CommonBundle.message("button.add") },
                                       Presentation.NULL_STRING,
                                       IconUtil.addIcon) {
      override fun actionPerformed(e: AnActionEvent) {
        val draft = presenter.createDraftRepository(remainingRepositories().map { it.name }.toList())
        val newNode = PyRepoNode(PyRepositoryListItem(draft, project, getAllNames = ::allRepoNames))
        addNode(newNode, myRoot)
        selectNodeInTree(newNode)
      }
    }
    
    val delete = MyDeleteAction { selectedNodes ->
      selectedNodes.all { it is MyNode && it !in defaultRepositoryNodes }
    }
    return listOf(add, delete)
  }

  override fun isModified(): Boolean = !hasAnyErrors() && super.isModified()

  override fun apply() {
    if (hasAnyErrors()) return
    super.apply()
    presenter.commitCustomRepositories(remainingRepositories().toList())
  }

  override fun reset() {
    // Order matters:
    //   1) presenter.rebuildTree() repopulates the tree from the persisted repository list,
    //      so the MasterDetailsComponent infrastructure sees the up-to-date set of nodes.
    //   2) super.reset() then clears selection / modification flags and refreshes the detail
    //      pane against those freshly created nodes.
    //   3) resetAllItems() reverts every node's editable form back to its stored repository
    //      state; it must run after super.reset() because super.reset() can swap in detail
    //      editors whose form state would otherwise stay dirty.
    presenter.rebuildTree(treeSink)
    super.reset()
    resetAllItems()
  }

  private val treeSink = object : RepositoryTreeSink {
    override fun removeAllRepositoryNodes() {
      val toRemove = TreeUtil.treeNodeTraverser(myRoot)
        .filter(MyNode::class.java)
        .asSequence()
        .filter { it != myRoot }
        .toList()
      toRemove.forEach { myRoot.remove(it) }
    }

    override fun addCustomRepositoryNode(repo: PyPackageRepository) {
      addNode(PyRepoNode(PyRepositoryListItem(repo, project, getAllNames = ::allRepoNames)), myRoot)
    }

    override fun reinstateDefaultRepositoryNodes() {
      defaultRepositoryNodes.forEach { addNode(it, myRoot) }
    }
  }

  override fun processRemovedItems() {
    presenter.clearCredentialsForRemoved(remainingRepositories().toList())
  }

  override fun wasObjectStored(editableObject: Any?): Boolean {
    val repo = editableObject as? PyPackageRepository ?: return false
    return presenter.isPersisted(repo)
  }

  override fun getDisplayName(): String {
    return PyBundle.message("python.packaging.repository.manage.dialog.name")
  }

  override fun removePaths(vararg paths: TreePath) {
    val filteredPaths = paths.filter { it.lastPathComponent !in defaultRepositoryNodes }.toTypedArray()
    super.removePaths(*filteredPaths)
  }

  private fun remainingRepositoryNodes(): Sequence<PyRepoNode> =
    TreeUtil.treeNodeTraverser(myRoot)
      .filter(PyRepoNode::class.java)
      .asSequence()
      .filter { it !in defaultRepositoryNodes }

  private fun allRepoNodes(): Sequence<PyRepoNode> =
    TreeUtil.treeNodeTraverser(myRoot)
      .filter(PyRepoNode::class.java)
      .asSequence()

  private fun remainingRepositories(): Sequence<PyPackageRepository> =
    remainingRepositoryNodes().map { it.item.repository }

  private fun allCustomRepoNames(): List<String> =
    remainingRepositoryNodes().map { it.item.displayName }.toList()

  private fun allRepoNames(): List<String> =
    allCustomRepoNames() + defaultRepositoryNodes.map { it.item.displayName }

  private fun hasAnyErrors(): Boolean =
    remainingRepositoryNodes().any { it.item.hasErrors() }

  internal fun resetAllItems() {
    allRepoNodes().forEach { it.item.reset() }
    myTree.repaint()
  }
}

private class PyRepoNode(val item: PyRepositoryListItem) : MasterDetailsComponent.MyNode(item)

/** Single typed cast site; every non-root node in [PyRepositoriesList] is a [PyRepoNode]. */
private val MasterDetailsComponent.MyNode.repoItem: PyRepositoryListItem
  get() = (this as PyRepoNode).item

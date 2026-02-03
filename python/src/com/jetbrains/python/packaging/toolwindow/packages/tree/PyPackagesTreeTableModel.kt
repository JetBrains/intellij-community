// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree

import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import javax.swing.JTree
import javax.swing.event.TreeModelListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * TreeTableModel implementation for PyPackagesTree.
 * Provides two columns: package name and package version.
 */
internal class PyPackagesTreeTableModel : TreeTableModel {
  companion object {
    private const val ROOT_NODE_NAME = "root"
    private const val COLUMN_COUNT = 2
    private const val NAME_COLUMN = 0
    private const val VERSION_COLUMN = 1
  }

  private val rootNode = DefaultMutableTreeNode(ROOT_NODE_NAME)
  private val treeModel = DefaultTreeModel(rootNode)
  private var tree: JTree? = null

  var items: List<DisplayablePackage> = emptyList()
    set(value) {
      field = value
      updateTreeData(value)
    }

  override fun getColumnCount(): Int = COLUMN_COUNT

  override fun getColumnName(column: Int): String = when (column) {
    NAME_COLUMN -> ""
    VERSION_COLUMN -> ""
    else -> ""
  }

  override fun getColumnClass(column: Int): Class<*> = when (column) {
    NAME_COLUMN -> TreeTableModel::class.java
    VERSION_COLUMN -> DisplayablePackage::class.java
    else -> Any::class.java
  }

  override fun getValueAt(node: Any, column: Int): Any? {
    val treeNode = node as? DefaultMutableTreeNode ?: return null
    val pkg = treeNode.userObject as? DisplayablePackage ?: return null

    return when (column) {
      NAME_COLUMN -> pkg
      VERSION_COLUMN -> pkg
      else -> null
    }
  }

  override fun isCellEditable(node: Any, column: Int): Boolean = false

  override fun setValueAt(aValue: Any?, node: Any, column: Int) {
    error("unsupported")
  }

  override fun getChild(parent: Any, index: Int): Any? = treeModel.getChild(parent, index)
  override fun getChildCount(parent: Any): Int = treeModel.getChildCount(parent)
  override fun getIndexOfChild(parent: Any, child: Any): Int = treeModel.getIndexOfChild(parent, child)
  override fun getRoot(): Any = treeModel.root
  override fun isLeaf(node: Any): Boolean = treeModel.isLeaf(node)
  override fun valueForPathChanged(path: TreePath, newValue: Any) = treeModel.valueForPathChanged(path, newValue)
  override fun addTreeModelListener(l: TreeModelListener) = treeModel.addTreeModelListener(l)
  override fun removeTreeModelListener(l: TreeModelListener) = treeModel.removeTreeModelListener(l)

  override fun setTree(tree: JTree) {
    this.tree = tree
  }

  private fun updateTreeData(packages: List<DisplayablePackage>) {
    rootNode.removeAllChildren()
    packages.forEach { pkg ->
      rootNode.add(createNodeRecursively(pkg))
    }
    treeModel.reload()
  }

  private fun createNodeRecursively(
    pkg: DisplayablePackage,
  ): DefaultMutableTreeNode {
    val node = DefaultMutableTreeNode(pkg)

    pkg.getRequirements().forEach { requirement ->
      node.add(createNodeRecursively(requirement))
    }

    return node
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.performancePlugin.remotedriver.dataextractor.computeOnEdt
import org.assertj.swing.core.Robot
import java.awt.Point
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class JChangesListViewFixture(private val robot: Robot, private val component: JTree) : JTreeTextFixture(robot, component) {

  fun addFileName(fileName: String) {
    handleCheckBoxWithFileName(fileName, true)
  }

  fun removeFileName(fileName: String) {
    handleCheckBoxWithFileName(fileName, false)
  }

  private fun handleCheckBoxWithFileName(fileName: String, setEnabled: Boolean) {
    val fileTreePathLocation = computeOnEdt {
      calcClickPoint(fileName, setEnabled)
    }
    if (fileTreePathLocation != null) {
      robot.click(component, fileTreePathLocation)
    }
  }

  private fun calcClickPoint(fileName: String, setEnabled: Boolean) : Point? {
    val root = component.model.root as DefaultMutableTreeNode
    val node = TreeUtil.findNode(root) { it.toString().contains(fileName) } ?: error("Node with name containing $fileName is not found")

    val fileTreePath = TreePath(node.path)
    val checkbox = getCheckBoxForNode(node, fileTreePath)

    if (checkbox.isSelected == setEnabled) return null

    val fileTreePathLocation = component.getPathBounds(fileTreePath) ?: error("Have not found bounds")
    val checkBoxBounds = checkbox.bounds

    val clickX = fileTreePathLocation.x + checkBoxBounds.width / 2
    val clickY = fileTreePathLocation.y + checkBoxBounds.height / 2

    return Point(clickX, clickY)
  }

  private fun getCheckBoxForNode(node: DefaultMutableTreeNode?, fileTreePath: TreePath): JCheckBox {
    val renderer = component.cellRenderer.getTreeCellRendererComponent(component, node,
                                                                       component.isPathSelected(fileTreePath),
                                                                       component.isExpanded(fileTreePath),
                                                                       component.model.isLeaf(node),
                                                                       component.getRowForPath(fileTreePath),
                                                                       component.isPathEditable(fileTreePath))

    val jpanel = renderer as? JPanel ?: error("Only JPanel is currently supported")
    val checkbox = jpanel.components.singleOrNull { it is JCheckBox } as? JCheckBox ?: error("Only JCheckBox is currently supported")
    return checkbox
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.driver.model.TreePathToRowListWithCheckboxState
import com.intellij.driver.model.TreePathToRowListWithCheckboxStateList
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextCellRendererReader
import com.jetbrains.performancePlugin.remotedriver.dataextractor.computeOnEdt
import org.assertj.swing.core.Robot
import org.assertj.swing.driver.BasicJTreeCellReader
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class JCheckboxTreeFixture(private val robot: Robot, private val component: JTree) : JTreeTextFixture(robot, component) {

  private val textCellReader = BasicJTreeCellReader(TextCellRendererReader())

  fun getCheckBoxForNode(fileTreePath: TreePath): JCheckBox {
    val node = fileTreePath.lastPathComponent as DefaultMutableTreeNode
    val renderer = computeOnEdt {
      component.cellRenderer.getTreeCellRendererComponent(component, fileTreePath.lastPathComponent as DefaultMutableTreeNode, component.isPathSelected(fileTreePath), component.isExpanded(fileTreePath), component.model.isLeaf(node), component.getRowForPath(fileTreePath), component.isPathEditable(fileTreePath))
    }
    val jpanel = renderer as? JPanel ?: error("Only JPanel is currently supported")
    val checkbox = jpanel.components.singleOrNull { it is JCheckBox } as? JCheckBox ?: error("Only JCheckBox is currently supported")
    return checkbox
  }

  private fun TreePath.toStringList(): List<String> {
    return computeOnEdt {
      val nodes = path.map { textCellReader.valueAt(component, it) ?: "" }.toMutableList()

      if (!component.isRootVisible) {
        nodes.removeAt(0)
      }

      return@computeOnEdt nodes
    }
  }

  fun collectCheckboxes(): TreePathToRowListWithCheckboxStateList {
    val result = TreePathToRowListWithCheckboxStateList()
    computeOnEdt {
      TreeUtil.visitVisibleRows(component, { path ->
        val root = component.model.root as DefaultMutableTreeNode
        TreePathToRowListWithCheckboxState(
          path.toStringList(),
          component.getRowForPath(path),
          getCheckBoxForNode(path).isSelected)
      }, {
          result.add(it)
           })
    }
    return result
  }


  fun switchCheckBoxByPath(path: List<String>, state: Boolean) {
    val root = component.model.root as DefaultMutableTreeNode

    val treePaths = collectExpandedPaths()

    val node = TreeUtil.findNode(root) { node ->
      TreePath(node.path).toStringList().equals(path)
    }

    val fileTreePath = TreePath(node!!.path)
    val checkbox = getCheckBoxForNode(fileTreePath)

    if (getCheckBoxForNode(fileTreePath).isSelected != state) {
      val checkboxLocation = computeOnEdt {
        component.getPathBounds(fileTreePath) ?: error("Have not found bounds")
      }
      while (getCheckBoxForNode(fileTreePath).isSelected != state) {
        clickOnCheckbox(checkbox, checkboxLocation)
      }
    }
  }

  private fun clickOnCheckbox(checkbox: JCheckBox, checkboxLocation: Rectangle) {
    val checkBoxBounds = checkbox.bounds

    val clickX = checkboxLocation.x + checkBoxBounds.width / 2
    val clickY = checkboxLocation.y + checkBoxBounds.height / 2

    robot.click(component, Point(clickX, clickY))
  }

}

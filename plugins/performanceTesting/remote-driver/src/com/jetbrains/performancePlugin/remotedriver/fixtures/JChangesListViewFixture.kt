package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.performancePlugin.remotedriver.dataextractor.computeOnEdt
import org.assertj.swing.core.Robot
import java.awt.Point
import java.awt.Rectangle
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
    val root = component.model.root as DefaultMutableTreeNode
    val node = TreeUtil.findNode(root) { it.toString().contains(fileName) } ?: error("Node with name containing $fileName is not found")

    val fileTreePath = TreePath(node.path)
    val checkbox = getCheckBoxForNode(node, fileTreePath)

    if (checkbox.isSelected != setEnabled) {
      val fileTreePathLocation = computeOnEdt {
        component.getPathBounds(fileTreePath) ?: error("Have not found bounds")
      }
      clickOnCheckbox(checkbox, fileTreePathLocation)
    }
  }

  private fun clickOnCheckbox(checkbox: JCheckBox, fileTrePathLocation: Rectangle) {
    val checkBoxBounds = checkbox.bounds

    val clickX = fileTrePathLocation.x + checkBoxBounds.width / 2
    val clickY = fileTrePathLocation.y + checkBoxBounds.height / 2

    robot.click(component, Point(clickX, clickY))
  }

  private fun getCheckBoxForNode(node: DefaultMutableTreeNode?, fileTreePath: TreePath): JCheckBox {
    val renderer = computeOnEdt {
      component.cellRenderer.getTreeCellRendererComponent(component, node,
                                                          component.isPathSelected(fileTreePath), component.isExpanded(fileTreePath),
                                                          component.model.isLeaf(node),
                                                          component.getRowForPath(fileTreePath),
                                                          component.isPathEditable(fileTreePath))
    }

    val jpanel = renderer as? JPanel ?: error("Only JPanel is currently supported")
    val checkbox = jpanel.components.singleOrNull { it is JCheckBox } as? JCheckBox ?: error("Only JCheckBox is currently supported")
    return checkbox
  }
}
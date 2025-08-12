// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.driver.model.TreePath
import com.intellij.driver.model.TreePathToRow
import com.intellij.driver.model.TreePathToRowList
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ReflectionUtil
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextCellRendererReader
import com.jetbrains.performancePlugin.remotedriver.dataextractor.computeOnEdt
import org.assertj.swing.awt.AWT
import org.assertj.swing.core.BasicComponentFinder
import org.assertj.swing.core.MouseButton
import org.assertj.swing.core.Robot
import org.assertj.swing.driver.BasicJTreeCellReader
import org.assertj.swing.driver.CellRendererReader
import org.assertj.swing.driver.ComponentPreconditions
import org.assertj.swing.fixture.JTreeFixture
import java.awt.Component
import java.awt.Container
import java.awt.Point
import javax.swing.Icon
import javax.swing.JTree

open class JTreeTextFixture(robot: Robot, private val component: JTree) : JTreeFixture(robot, component) {
  private var cellReader = BasicJTreeCellReader(TextCellRendererReader())

  init {
    replaceCellReader(cellReader)
  }

  fun replaceCellRendererReader(reader: CellRendererReader) {
    cellReader = BasicJTreeCellReader(reader)
    replaceCellReader(cellReader)
  }

  override fun clickRow(row: Int): JTreeFixture {
    robot().click(target(), scrollToRowAndGetVisibleCenter(row))
    return this
  }

  override fun rightClickRow(row: Int): JTreeFixture {
    robot().click(target(), scrollToRowAndGetVisibleCenter(row), MouseButton.RIGHT_BUTTON, 1)
    return this
  }

  override fun doubleClickRow(row: Int): JTreeFixture {
    robot().click(target(), scrollToRowAndGetVisibleCenter(row), MouseButton.LEFT_BUTTON, 2)
    return this
  }

  fun getRowPoint(row: Int): Point = computeOnEdt {
    require(row in 0 until component.rowCount) {
      "The given row $row should be between 0 and ${component.rowCount - 1}"
    }
    component.scrollRowToVisible(row)
    component.getRowBounds(row).location
  }

  /**
   * Collects all paths in the tree component along with their corresponding row indices.
   * The method does not expand nodes.
   */
  fun collectExpandedPaths(): TreePathToRowList {
    val result = TreePathToRowList()
    computeOnEdt {
      val paths = mutableListOf<List<String>>()

      TreeUtil.visitVisibleRows(component, { path ->
        val nodes = mutableListOf<String>()
        path.path.forEach { nodes.add(cellReader.valueAt(component, it) ?: "") }
        if (component.isRootVisible.not()) {
          nodes.removeAt(0)
        }
        nodes
      }, { paths.add(it) })
      return@computeOnEdt paths
    }.forEachIndexed { index, path ->
      result.add(TreePathToRow(path.filter { it.isNotEmpty() }, index))
    }
    return result
  }

  fun areTreeNodesLoaded(): Boolean {
    var isLoaded = true
    computeOnEdt {
      TreeUtil.visitVisibleRows(component) { path ->
        isLoaded = !TreeUtil.isLoadingPath(path)
        if (!isLoaded) TreeVisitor.Action.INTERRUPT else TreeVisitor.Action.CONTINUE
      }
    }
    return isLoaded
  }

  fun collectSelectedPaths(): List<TreePath> {
    return computeOnEdt {
      component.selectionPaths
    }?.map { path ->
      path.path.map { computeOnEdt { cellReader.valueAt(component, it) } ?: "" }.run {
        if (component.isRootVisible) subList(1, size)
        else this
      }
    }?.map(::TreePath) ?: emptyList()
  }

  fun expandAll(timeoutMs: Int) {
    computeOnEdt { TreeUtil.promiseExpandAll(component) }.blockingGet(timeoutMs)
  }

  fun collectIconsAtRow(row: Int): List<Icon> {
    val rowComponent = getComponentAtRow(row)
    if (rowComponent is Container) {
      return BasicComponentFinder.finderWithCurrentAwtHierarchy().findAll(rowComponent) {
        ReflectionUtil.getMethod(it.javaClass, "getIcon") != null
      }.mapNotNull { ReflectionUtil.getMethod(it.javaClass, "getIcon")!!.invoke(it) as? Icon }
    }
    return emptyList()
  }

  fun getComponentAtRow(row: Int): Component {
    return computeOnEdt {
      val tree = target()
      tree.cellRenderer.getTreeCellRendererComponent(tree,
                                                     tree.getPathForRow(row).lastPathComponent,
                                                     tree.isRowSelected(row),
                                                     tree.isExpanded(row),
                                                     false,
                                                     row,
                                                     tree.hasFocus() && tree.isRowSelected(row))
    }
  }

  private fun scrollToRowAndGetVisibleCenter(row: Int): Point {
    return computeOnEdt {
      val tree = target()
      ComponentPreconditions.checkEnabledAndShowing(tree)
      tree.checkRowInBounds(row)
      tree.scrollRowToVisible(row)
      AWT.centerOf(tree.visibleRect.intersection(tree.getRowBounds(row)))
    }
  }

  private fun JTree.checkRowInBounds(row: Int): Int {
    val rowCount = getRowCount()
    if (row in 0..<rowCount) {
      return row
    }
    throw IndexOutOfBoundsException("The given row <$row> should be between <0> and <$rowCount>")
  }
}
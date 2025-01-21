package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.driver.model.TreePath
import com.intellij.driver.model.TreePathToRow
import com.intellij.driver.model.TreePathToRowList
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextCellRendererReader
import com.jetbrains.performancePlugin.remotedriver.dataextractor.computeOnEdt
import org.assertj.swing.core.Robot
import org.assertj.swing.driver.BasicJTreeCellReader
import org.assertj.swing.driver.CellRendererReader
import org.assertj.swing.fixture.JTreeFixture
import java.awt.Component
import java.awt.Point
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

  fun getRowPoint(row: Int): Point = computeOnEdt {
    require(row in 0 until component.rowCount) {
      "The given row $row should be between 0 and ${component.rowCount - 1}"
    }
    component.scrollRowToVisible(row)
    component.getRowBounds(row).location
  }

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
      result.add(TreePathToRow(path.filterNotNull().filter { it.isNotEmpty() }, index))
    }
    return result
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
    computeOnEdt {
      TreeUtil.promiseExpandAll(component).blockingGet(timeoutMs)
    }
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
}
package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.driver.model.TreePath
import com.intellij.driver.model.TreePathToRow
import com.intellij.driver.model.TreePathToRowList
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextCellRendererReader
import com.jetbrains.performancePlugin.remotedriver.dataextractor.computeOnEdt
import org.assertj.swing.core.Robot
import org.assertj.swing.driver.BasicJTreeCellReader
import org.assertj.swing.fixture.JTreeFixture
import javax.swing.JTree

open class JTreeTextFixture(robot: Robot, private val component: JTree) : JTreeFixture(robot, component) {
  private val cellReader = BasicJTreeCellReader(TextCellRendererReader())

  init {
    replaceCellReader(cellReader)
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
      path.path.map { cellReader.valueAt(component, it) ?: "" }.run {
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
}
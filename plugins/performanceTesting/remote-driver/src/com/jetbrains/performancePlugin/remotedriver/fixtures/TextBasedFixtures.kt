package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.driver.model.TreePathToRow
import com.intellij.driver.model.TreePathToRowList
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.performancePlugin.remotedriver.dataextractor.*
import com.jetbrains.performancePlugin.remotedriver.dataextractor.JComboBoxTextCellReader
import com.jetbrains.performancePlugin.remotedriver.dataextractor.JListTextCellReader
import com.jetbrains.performancePlugin.remotedriver.dataextractor.JTableTextCellReader
import com.jetbrains.performancePlugin.remotedriver.dataextractor.JTreeTextCellReader
import org.assertj.swing.core.Robot
import org.assertj.swing.fixture.JComboBoxFixture
import org.assertj.swing.fixture.JListFixture
import org.assertj.swing.fixture.JTableFixture
import org.assertj.swing.fixture.JTreeFixture
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree

class JTreeTextFixture(robot: Robot, private val component: JTree) : JTreeFixture(robot, component) {
  private val cellReader = JTreeTextCellReader()

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
}

class JTableTextFixture(robot: Robot, component: JTable) : JTableFixture(robot, component) {
  init {
    replaceCellReader(JTableTextCellReader())
  }
}

class JListTextFixture(robot: Robot, component: JList<*>) : JListFixture(robot, component) {
  init {
    replaceCellReader(JListTextCellReader())
  }
}

class JComboBoxTextFixture(robot: Robot, component: JComboBox<*>) : JComboBoxFixture(robot, component) {
  init {
    replaceCellReader(JComboBoxTextCellReader())
  }
}
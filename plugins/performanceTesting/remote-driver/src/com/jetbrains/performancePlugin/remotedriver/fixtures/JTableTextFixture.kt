package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.driver.model.StringTable
import com.jetbrains.performancePlugin.remotedriver.dataextractor.JTableTextCellReader
import org.assertj.swing.core.Robot
import org.assertj.swing.data.TableCell.row
import org.assertj.swing.fixture.JTableFixture
import javax.swing.JTable

class JTableTextFixture(robot: Robot, component: JTable) : JTableFixture(robot, component) {
  init {
    replaceCellReader(JTableTextCellReader())
  }

  fun collectItems(): StringTable {
    return StringTable().also { table ->
      contents().forEachIndexed { rowNumber, rows ->
        table[rowNumber] = HashMap<Int, String>().apply {
          rows.forEachIndexed { columnNumber, value -> put(columnNumber, value) }
        }
      }
    }
  }

  fun clickCell(row: Int, column: Int) = cell(row(row).column(column)).click()
  fun rightClickCell(row: Int, column: Int) = cell(row(row).column(column)).rightClick()
  fun doubleClickCell(row: Int, column: Int) = cell(row(row).column(column)).doubleClick()
}
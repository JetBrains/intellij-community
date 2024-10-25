package com.jetbrains.performancePlugin.remotedriver.fixtures

import com.intellij.driver.model.StringTable
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextCellRendererReader
import org.assertj.swing.core.Robot
import org.assertj.swing.data.TableCell.row
import org.assertj.swing.driver.BasicJTableCellReader
import org.assertj.swing.driver.CellRendererReader
import org.assertj.swing.fixture.JTableFixture
import java.awt.Dimension
import javax.swing.JTable

class JTableTextFixture(robot: Robot, component: JTable) : JTableFixture(robot, component) {
  init {
    replaceCellReader(BasicJTableCellReader(TextCellRendererReader(Dimension(component.width, 100))))
  }

  fun replaceCellRendererReader(reader: CellRendererReader) {
    replaceCellReader(BasicJTableCellReader(reader))
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

  fun clickCell(row: Int, column: Int) {
    cell(row(row).column(column)).click()
  }

  fun rightClickCell(row: Int, column: Int) {
    cell(row(row).column(column)).rightClick()
  }

  fun doubleClickCell(row: Int, column: Int) {
    cell(row(row).column(column)).doubleClick()
  }
}
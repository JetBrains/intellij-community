package com.jetbrains.performancePlugin.remotedriver.dataextractor

import org.assertj.swing.cell.JTableCellReader
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.JTable

internal class JTableTextCellReader : JTableCellReader {
  override fun valueAt(table: JTable, row: Int, column: Int): String {
    return computeOnEdt {
      val component = table
        .getCellRenderer(row, column)
        .getTableCellRendererComponent(JTable(), table.getValueAt(row, column), true, true, row, column)
      component.size = Dimension(table.width, 100)
      TextParser.parseCellRenderer(component).joinToString(" ") { it.trim() }
    }
  }

  override fun fontAt(table: JTable, row: Int, column: Int): Font = table.font

  override fun backgroundAt(table: JTable, row: Int, column: Int): Color = table.background

  override fun foregroundAt(table: JTable, row: Int, column: Int): Color = table.foreground
}
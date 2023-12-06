package com.jetbrains.performancePlugin.remotedriver.dataextractor

import org.assertj.swing.cell.JListCellReader
import java.awt.Dimension
import javax.swing.JList
import javax.swing.ListCellRenderer

internal class JListTextCellReader : JListCellReader {
  override fun valueAt(list: JList<*>?, index: Int): String? {
    require(list != null)
    return computeOnEdt {
      @Suppress("UNCHECKED_CAST") val renderer = list.cellRenderer as ListCellRenderer<Any>
      val c = renderer.getListCellRendererComponent(JList(), list.model.getElementAt(index), index, true, true)
      c.size = Dimension(list.width, 100)
      TextParser.parseCellRenderer(c).joinToString(" ") { it.trim() }
    }
  }
}
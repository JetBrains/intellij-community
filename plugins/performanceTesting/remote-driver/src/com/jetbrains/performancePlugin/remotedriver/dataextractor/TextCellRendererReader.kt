package com.jetbrains.performancePlugin.remotedriver.dataextractor

import org.assertj.swing.driver.CellRendererReader
import java.awt.Component
import java.awt.Dimension

class TextCellRendererReader(private val componentSizeToSet: Dimension? = null) : CellRendererReader {
  override fun valueFrom(c: Component?): String? {
    return if (c != null) {
      c.size = componentSizeToSet ?: c.preferredSize
      TextParser.parseCellRenderer(c).joinToString(" ") { it.trim() }
    } else {
      ""
    }
  }
}
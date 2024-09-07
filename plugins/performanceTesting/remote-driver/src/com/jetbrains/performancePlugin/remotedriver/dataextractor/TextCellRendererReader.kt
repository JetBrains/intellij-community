package com.jetbrains.performancePlugin.remotedriver.dataextractor

import org.assertj.swing.driver.CellRendererReader
import java.awt.Component

class TextCellRendererReader : CellRendererReader {
  override fun valueFrom(c: Component?): String? {
    return if (c != null) {
      c.size = c.preferredSize
      TextParser.parseCellRenderer(c).joinToString(" ") { it.trim() }
    } else {
      ""
    }
  }
}
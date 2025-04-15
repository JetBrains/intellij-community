package com.jetbrains.performancePlugin.remotedriver.dataextractor

import com.intellij.driver.model.TextData
import org.assertj.swing.driver.CellRendererReader
import java.awt.Component
import java.awt.Dimension

class TextCellRendererReader(private val componentSizeToSet: Dimension? = null) : CellRendererReader {
  override fun valueFrom(c: Component?): String? {
    return if (c != null) {
      c.size = componentSizeToSet ?: c.preferredSize
      TextParser.parseCellRenderer(c)
        .sortedWith(compareBy<TextData> { it.point.x }.thenComparing { it.point.y })
        .joinToString(" ") { it.text.trim() }
    } else {
      ""
    }
  }
}
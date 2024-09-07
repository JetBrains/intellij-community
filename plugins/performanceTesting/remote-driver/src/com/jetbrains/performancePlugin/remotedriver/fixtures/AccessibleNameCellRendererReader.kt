package com.jetbrains.performancePlugin.remotedriver.fixtures

import org.assertj.swing.driver.CellRendererReader
import java.awt.Component

class AccessibleNameCellRendererReader : CellRendererReader {
  override fun valueFrom(c: Component?): String? {
    return c?.accessibleContext?.accessibleName ?: ""
  }
}
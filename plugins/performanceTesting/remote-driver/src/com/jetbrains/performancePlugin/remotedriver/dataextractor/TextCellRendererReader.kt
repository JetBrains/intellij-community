// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.dataextractor

import com.intellij.driver.model.TextData
import org.assertj.swing.driver.CellRendererReader
import java.awt.Component
import java.awt.Container
import java.awt.Dimension

class TextCellRendererReader(private val componentSizeToSet: Dimension? = null) : CellRendererReader {
  override fun valueFrom(c: Component?): String? {
    return if (c != null) {
      c.size = componentSizeToSet ?: c.preferredSize
      layoutTree(c)
      TextParser.parseCellRenderer(c)
        .sortedWith(compareBy<TextData> { it.point.x }.thenComparing { it.point.y })
        .joinToString(" ") { it.text.trim() }
    } else {
      ""
    }
  }

  /** Lays out [c] and each nested container, so that each nested component gets its size before the paint. */
  private fun layoutTree(c: Component) {
    if (c is Container) {
      c.doLayout()
      c.components.forEach(::layoutTree)
    }
  }
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.ide.ui.search.SearchUtil
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener

/**
 * Renders a simple labels strip above the table that tracks the live column widths. We can't
 * use JTable's own `tableHeader` because making it visible breaks click-to-edit on this page;
 * `tableHeader = null` is the only configuration that works reliably here.
 */
internal fun buildHeaderBar(table: JTable): JComponent {
  val columnModel = table.columnModel
  val bar = object : javax.swing.JPanel(null) {
    override fun doLayout() {
      var x = 0
      for (i in 0 until columnModel.columnCount) {
        val col = columnModel.getColumn(i)
        val child = if (i < componentCount) getComponent(i) else continue
        child.setBounds(x + JBUI.scale(5), 0, (col.width - JBUI.scale(10)).coerceAtLeast(0), height)
        x += col.width
      }
    }

    override fun getPreferredSize(): Dimension =
      Dimension(super.getPreferredSize().width, JBUI.scale(22))
  }.apply {
    isOpaque = true
    background = UIUtil.getPanelBackground()
    border = JBUI.Borders.customLineBottom(JBColor.border())
    // Don't index "Tool", "Lookup", "Executable Path" as global Settings search hits.
    // Belt-and-braces: the property excludes us from `TraverseUIStarter`'s build-time
    // index, while [HeaderText] (a non-JLabel) keeps the runtime spotlight in
    // `SearchUtil.traverseComponentsTree` from matching either — that traversal scrapes
    // text via `JLabel#getText`, so a paint-only component is invisible to it.
    ClientProperty.put(this, SearchUtil.SEARCH_SKIP_COMPONENT_KEY, true)
  }
  for (i in 0 until columnModel.columnCount) {
    val col = columnModel.getColumn(i)
    bar.add(HeaderText(col.headerValue?.toString().orEmpty()))
  }
  columnModel.addColumnModelListener(object : TableColumnModelListener {
    override fun columnAdded(e: TableColumnModelEvent) {}
    override fun columnRemoved(e: TableColumnModelEvent) {}
    override fun columnMoved(e: TableColumnModelEvent) {
      bar.revalidate(); bar.repaint()
    }

    override fun columnMarginChanged(e: ChangeEvent) {
      bar.revalidate(); bar.repaint()
    }

    override fun columnSelectionChanged(e: ListSelectionEvent) {}
  })
  return bar
}

/**
 * Paint-only header label. We avoid [com.intellij.ui.components.JBLabel] / [javax.swing.JLabel]
 * on purpose so the Settings search spotlight (which scrapes text via `JLabel#getText` in
 * `SearchUtil.traverseComponentsTree`) can't match the column titles — they describe
 * table layout, not user-facing options.
 */
private class HeaderText(private val displayText: String) : javax.swing.JComponent() {
  init {
    font = UIUtil.getLabelFont()
  }

  override fun getPreferredSize(): Dimension {
    val fm = getFontMetrics(font)
    return Dimension(fm.stringWidth(displayText), fm.height)
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val g2 = g.create() as Graphics2D
    try {
      GraphicsUtil.applyRenderingHints(g2)
      g2.font = font
      g2.color = UIUtil.getLabelForeground()
      val fm = g2.fontMetrics
      g2.drawString(displayText, 0, (height + fm.ascent - fm.descent) / 2)
    }
    finally {
      g2.dispose()
    }
  }
}

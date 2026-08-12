// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.ide.ui.search.SearchUtil
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Scrollable

/**
 * A thin, static "Tool" / "Lookup" caption strip installed as the scroll pane's column header, so it
 * shares the viewport's width and origin and its columns line up with the rows below. Implements
 * [Scrollable] with [getScrollableTracksViewportWidth] = true so it stretches to the full viewport
 * width (excluding the vertical scrollbar), letting its right-anchored "Lookup" column meet the rows'.
 */
internal fun buildHeaderBar(): JComponent {
  return object : JPanel(BorderLayout()), Scrollable {
    override fun getPreferredSize(): Dimension =
      Dimension(super.getPreferredSize().width, JBUI.scale(24))

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false
  }.apply {
    isOpaque = true
    background = UIUtil.getPanelBackground()
    border = JBUI.Borders.compound(
      JBUI.Borders.customLineBottom(JBColor.border()),
      JBUI.Borders.empty(0, 8),
    )
    // Don't index "Tool" / "Lookup" as global Settings search hits — they describe layout, not options.
    ClientProperty.put(this, SearchUtil.SEARCH_SKIP_COMPONENT_KEY, true)
    add(HeaderText(PyToolsUiBundle.message("settings.external.tools.column.name")), BorderLayout.WEST)
    // Mirror each row's right side so the caption's right edge lines up with the chain's right edge,
    // just left of the toggle column.
    val right = JPanel().apply {
      isOpaque = false
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      add(HeaderText(PyToolsUiBundle.message("settings.external.tools.column.mode")))
      add(Box.createHorizontalStrut(columnGap()))
      add(Box.createHorizontalStrut(toggleColumnWidth()))
    }
    add(right, BorderLayout.EAST)
  }
}

/**
 * Paint-only header caption. We avoid [com.intellij.ui.components.JBLabel] / [javax.swing.JLabel]
 * on purpose so the Settings search spotlight (which scrapes text via `JLabel#getText` in
 * `SearchUtil.traverseComponentsTree`) can't match the captions.
 */
private class HeaderText(private val displayText: String) : JComponent() {
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

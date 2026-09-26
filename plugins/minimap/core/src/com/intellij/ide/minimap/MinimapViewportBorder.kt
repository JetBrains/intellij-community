// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
import javax.swing.border.Border

internal class MinimapViewportBorder(
  private val scrollPane: JScrollPane,
  private val minimap: Component,
  private val delegate: Border?,
) : Border {
  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    delegate?.paintBorder(c, g, x, y, width, height)
  }

  override fun getBorderInsets(c: Component): Insets {
    val insets = delegate?.getBorderInsets(c)?.copy() ?: JBUI.emptyInsets()
    val reservedWidth = reservedWidth()
    if (isMinimapVerticalScrollBarOnLeft(scrollPane)) {
      insets.left += reservedWidth
    }
    else {
      insets.right += reservedWidth
    }
    return insets
  }

  override fun isBorderOpaque(): Boolean = delegate?.isBorderOpaque ?: false

  private fun reservedWidth(): Int {
    if (!minimap.isVisible) return 0
    val minimapWidth = minimap.preferredSize.width.coerceAtLeast(0)
    if (minimapWidth == 0) return 0

    val vsb = scrollPane.verticalScrollBar ?: return minimapWidth
    if (!vsb.isEnabled || scrollPane.verticalScrollBarPolicy == VERTICAL_SCROLLBAR_NEVER) return minimapWidth

    val shouldReserveOverlappingVsb = scrollPane.verticalScrollBarPolicy == VERTICAL_SCROLLBAR_ALWAYS || vsb.isVisible
    if (!shouldReserveOverlappingVsb) return minimapWidth

    val vsbNeedsSpace = (scrollPane as? JBScrollPane)?.verticalScrollBarNeedsSpace() ?: vsb.isOpaque
    if (vsbNeedsSpace) return minimapWidth

    val preferredVsbWidth = vsb.preferredSize?.width ?: 0
    return minimapWidth + maxOf(vsb.width, preferredVsbWidth).coerceAtLeast(0)
  }
}

internal fun isMinimapVerticalScrollBarOnLeft(scrollPane: JScrollPane): Boolean {
  val property = scrollPane.getClientProperty(JBScrollPane.Flip::class.java)
  val flip = property as? JBScrollPane.Flip ?: JBScrollPane.Flip.NONE
  return if (scrollPane.componentOrientation.isLeftToRight) {
    flip == JBScrollPane.Flip.BOTH || flip == JBScrollPane.Flip.HORIZONTAL
  }
  else {
    flip == JBScrollPane.Flip.NONE || flip == JBScrollPane.Flip.VERTICAL
  }
}

private fun Insets.copy(): Insets = JBUI.insets(top, left, bottom, right)

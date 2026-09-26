// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.breakpoints

import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import kotlin.math.min

class MinimapBreakpointPainter {
  fun paint(graphics: Graphics2D, entries: List<MinimapBreakpointEntry>) {
    if (entries.isEmpty()) return

    val clipBounds = graphics.clipBounds
    val lineHighlightX = clipBounds?.x?.toDouble() ?: 0.0
    val lineHighlightWidth = clipBounds?.width?.toDouble() ?: 0.0

    val oldComposite = graphics.composite
    val oldAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)

    try {
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      for (entry in entries) {
        if (entry.hasLineHighlight && lineHighlightWidth > 0.0) {
          val highlightRect = Rectangle2D.Double(
            lineHighlightX,
            entry.rect2d.y,
            lineHighlightWidth,
            entry.rect2d.height.coerceAtLeast(1.0)
          )
          graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, BREAKPOINT_LINE_ALPHA)
          graphics.color = entry.color
          graphics.fill(highlightRect)
        }

        val diameter = dotDiameter(entry)
        val x = entry.rect2d.x + (entry.rect2d.width - diameter) / 2.0
        val y = entry.rect2d.y + (entry.rect2d.height - diameter) / 2.0
        val dot = Ellipse2D.Double(x, y, diameter, diameter)
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, BREAKPOINT_DOT_ALPHA)
        graphics.color = entry.color
        graphics.fill(dot)
      }
    }
    finally {
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing)
      graphics.composite = oldComposite
    }
  }

  private fun dotDiameter(entry: MinimapBreakpointEntry): Double {
    val availableWidth = (entry.rect2d.width - DOT_SIDE_PADDING_PX * 2.0).coerceAtLeast(1.0)
    val availableHeight = (entry.rect2d.height - DOT_VERTICAL_PADDING_PX * 2.0).coerceAtLeast(1.0)
    return min(min(availableWidth, availableHeight), MAX_DOT_DIAMETER_PX)
  }

  companion object {
    private const val BREAKPOINT_DOT_ALPHA: Float = 1.0f
    private const val BREAKPOINT_LINE_ALPHA: Float = 0.5f
    private const val DOT_SIDE_PADDING_PX: Double = 0.6
    private const val DOT_VERTICAL_PADDING_PX: Double = 0.4
    private const val MAX_DOT_DIAMETER_PX: Double = 5.0
  }
}

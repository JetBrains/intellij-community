// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.folding

import com.intellij.ide.minimap.breakpoints.MinimapBreakpointEntry
import com.intellij.ide.minimap.layout.MinimapLayoutMetrics
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D

class MinimapFoldMarkerPainter {
  fun paint(
    graphics: Graphics2D,
    entries: List<MinimapFoldMarkerEntry>,
    breakpointEntries: List<MinimapBreakpointEntry>,
    metrics: MinimapLayoutMetrics?,
    markerColor: Color,
  ) {
    if (entries.isEmpty()) return

    val linesWithBreakpoints = breakpointEntries.asSequence().map { it.projectedLine }.toHashSet()
    val oldComposite = graphics.composite
    val oldStroke = graphics.stroke
    val oldColor = graphics.color
    try {
      for (entry in entries) {
        if (linesWithBreakpoints.contains(entry.projectedLine)) continue
        drawGutterMarker(graphics, entry, markerColor)
        drawContentLine(graphics, entry, metrics, markerColor)
      }
    }
    finally {
      graphics.color = oldColor
      graphics.stroke = oldStroke
      graphics.composite = oldComposite
    }
  }

  private fun drawGutterMarker(graphics: Graphics2D, entry: MinimapFoldMarkerEntry, markerColor: Color) {
    val rect = entry.rect2d
    if (rect.width <= 0.0 || rect.height <= 0.0) return

    val centerY = rect.y + rect.height / 2.0
    val rightX = rect.x + rect.width - MARKER_SIDE_PADDING
    val leftX = (rightX - CHEVRON_WIDTH).coerceAtLeast(rect.x + MARKER_SIDE_PADDING)
    if (rightX <= leftX) return

    graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, MARKER_ALPHA)
    graphics.stroke = BasicStroke(MARKER_STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    graphics.color = markerColor
    val chevronTopY = centerY - CHEVRON_HALF_HEIGHT
    val chevronBottomY = centerY + CHEVRON_HALF_HEIGHT
    graphics.drawLine(leftX.toInt(), chevronTopY.toInt(), rightX.toInt(), centerY.toInt())
    graphics.drawLine(leftX.toInt(), chevronBottomY.toInt(), rightX.toInt(), centerY.toInt())
  }

  private fun drawContentLine(graphics: Graphics2D, entry: MinimapFoldMarkerEntry, metrics: MinimapLayoutMetrics?, markerColor: Color) {
    val metrics = metrics ?: return

    val rect = entry.rect2d
    val centerY = rect.y + rect.height / 2.0
    val contentStartX = metrics.contentStartX + CONTENT_LINE_SIDE_PADDING
    val contentEndX = metrics.contentStartX + metrics.contentWidth - CONTENT_LINE_SIDE_PADDING
    if (contentEndX - contentStartX < 1.0) return

    graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, CONTENT_LINE_ALPHA)
    graphics.stroke = BasicStroke(CONTENT_LINE_STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    graphics.color = markerColor
    graphics.drawLine(contentStartX.toInt(), centerY.toInt(), contentEndX.toInt(), centerY.toInt())
  }

  companion object {
    private const val MARKER_ALPHA: Float = 0.9f
    private const val MARKER_STROKE_WIDTH: Float = 1.1f
    private const val MARKER_SIDE_PADDING: Double = 1.0
    private const val CHEVRON_WIDTH: Double = 3.0
    private const val CHEVRON_HALF_HEIGHT: Double = 1.6
    private const val CONTENT_LINE_ALPHA: Float = 0.5f
    private const val CONTENT_LINE_STROKE_WIDTH: Float = 0.9f
    private const val CONTENT_LINE_SIDE_PADDING: Double = 0.8
  }
}

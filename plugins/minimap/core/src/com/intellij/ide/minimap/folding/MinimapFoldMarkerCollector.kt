// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.folding

import com.intellij.ide.minimap.layout.MinimapLayoutMetrics
import com.intellij.ide.minimap.layout.MinimapLayoutUtil
import com.intellij.ide.minimap.render.MinimapRenderContext

class MinimapFoldMarkerCollector {
  fun buildEntries(
    context: MinimapRenderContext,
    metrics: MinimapLayoutMetrics?,
  ): List<MinimapFoldMarkerEntry> {
    val metrics = metrics ?: return emptyList()
    val collapsedRegions = context.lineProjection.collapsedRegions
    if (collapsedRegions.isEmpty()) return emptyList()

    val gutterWidth = metrics.contentStartX
    if (gutterWidth <= 0.0) return emptyList()

    val visibleLines = MinimapLayoutUtil.visibleLines(context.geometry, metrics.lineCount)
    if (visibleLines.isEmpty()) return emptyList()

    val result = ArrayList<MinimapFoldMarkerEntry>(collapsedRegions.size)
    for (region in collapsedRegions) {
      if (region.projectedLine !in visibleLines) continue
      val rect = MinimapLayoutUtil.rectFromDoubles(
        x1 = 0.0,
        x2 = gutterWidth,
        y1 = region.projectedLine * metrics.baseLineHeight,
        y2 = (region.projectedLine + 1) * metrics.baseLineHeight,
        areaStart = context.geometry.areaStart.toDouble(),
        maxWidth = gutterWidth,
      )
      result += MinimapFoldMarkerEntry(
        projectedLine = region.projectedLine,
        collapsedLineCount = region.hiddenLineCount,
        rect2d = rect,
      )
    }

    return result
  }
}

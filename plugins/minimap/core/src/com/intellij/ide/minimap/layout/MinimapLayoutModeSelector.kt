// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layout

import com.intellij.ide.minimap.render.MinimapRenderContext
import com.intellij.ide.minimap.settings.MinimapScaleMode

object MinimapLayoutModeSelector {
  private const val DENSE_LINES_PER_PIXEL = 2.0

  fun selectMode(context: MinimapRenderContext, scaleMode: MinimapScaleMode): MinimapLayoutMode {
    if (scaleMode != MinimapScaleMode.FIT) return MinimapLayoutMode.EXACT

    val minimapHeight = context.geometry.minimapHeight
    val lineCount = context.lineProjection.projectedLineCount
    if (minimapHeight <= 0 || lineCount <= 0) return MinimapLayoutMode.EXACT

    val linesPerPixel = lineCount.toDouble() / minimapHeight.toDouble()
    return if (linesPerPixel >= DENSE_LINES_PER_PIXEL) MinimapLayoutMode.DENSE else MinimapLayoutMode.EXACT
  }
}

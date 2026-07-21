// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.geometry

import com.intellij.ide.minimap.layout.MinimapLayoutUtil
import com.intellij.ide.minimap.settings.MinimapScaleMode
import com.intellij.ide.minimap.thumb.MinimapThumb
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import kotlin.math.min

class MinimapGeometryCalculator(private val editor: Editor) {
  fun compute(panelHeight: Int,
              scaleData: MinimapScaleData,
              scaleMode: MinimapScaleMode,
              projectedLineCount: Int,
              areaStartOverride: Int? = null): MinimapGeometryData {
    val visibleArea = editor.scrollingModel.visibleArea
    val contentHeight = MinimapScaleUtil.contentHeight(editor, projectedLineCount)

    val rightMargin = MinimapLayoutUtil.getRightMarginChars(editor)
    val charWidth = EditorUtil.getPlainSpaceWidth(editor)

    val logicalWidth = MinimapLayoutUtil.computeLogicalWidth(rightMargin, charWidth, visibleArea.width)
    val minimapWidth = scaleData.width
    val minimapHeight = if (scaleMode == MinimapScaleMode.FIT && scaleData.fitToHeight) {
      panelHeight.coerceAtLeast(0)
    }
    else {
      (contentHeight * minimapWidth / logicalWidth.toDouble()).toInt().coerceAtLeast(0)
    }

    val visibleHeight = min(visibleArea.height, contentHeight).coerceAtLeast(0)
    val scrollRange = (contentHeight - visibleHeight).coerceAtLeast(0)
    val thumbHeight = MinimapThumb.computeHeight(visibleHeight, contentHeight, minimapHeight)
    val thumbStart = MinimapThumb.computeStart(visibleArea.y, scrollRange, minimapHeight, thumbHeight)

    val panelSpan = (minimapHeight - panelHeight).coerceAtLeast(0)
    val areaStart = areaStartOverride?.coerceIn(0, panelSpan) ?: if (minimapHeight > thumbHeight) {
      val maxScroll = (minimapHeight - thumbHeight).coerceAtLeast(1)
      val scrollPosition = thumbStart / maxScroll.toFloat()
      (scrollPosition * panelSpan).toInt().coerceAtLeast(0)
    }
    else {
      0
    }

    val areaEnd = areaStart + min(panelHeight, minimapHeight)

    return MinimapGeometryData(
      minimapHeight = minimapHeight,
      areaStart = areaStart,
      areaEnd = areaEnd,
      thumbStart = thumbStart,
      thumbHeight = thumbHeight
    )
  }
}

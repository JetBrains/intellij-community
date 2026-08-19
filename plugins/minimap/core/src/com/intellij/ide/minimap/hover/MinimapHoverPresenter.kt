// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.ide.minimap.MinimapUsageCollector
import com.intellij.ide.minimap.geometry.MinimapLineGeometryUtil
import com.intellij.ide.minimap.render.MinimapRenderContext
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import kotlin.math.roundToInt

class MinimapHoverPresenter(private val panel: MinimapPanel) {
  private val hoverPainter = MinimapHoverPainter()
  private val balloonController = MinimapBalloonController(panel)
  private var activeTarget: MinimapHoverTarget? = null
  private var lastContext: MinimapRenderContext? = null

  fun setContext(context: MinimapRenderContext?) {
    lastContext = context
  }

  fun setTarget(target: MinimapHoverTarget?) {
    if (target == null && activeTarget == null) return
    if (target != null && target.sameAs(activeTarget)) return
    activeTarget = target
    if (target == null) {
      balloonController.hide()
      return
    }
    MinimapUsageCollector.logHoverShown(
      scaleMode = panel.settings.state.scaleMode,
    )
    balloonController.show(target.text, target.rect, target.icon)
  }

  fun paint(graphics: Graphics2D) {
    val target = activeTarget ?: return
    val context = lastContext ?: return

    val lineHeight = computeLineHeight(context)
    val leftInset = hoverFrameLeftInset()
    if (leftInset == 0) {
      hoverPainter.paint(graphics, target.rect, target.declarationWidth, lineHeight, hoverColor())
    }
    else {
      val rect = Rectangle(target.rect)
      rect.x += leftInset
      rect.width = (rect.width - leftInset).coerceAtLeast(1)
      val declarationWidth = (target.declarationWidth - leftInset).coerceAtLeast(1)
      hoverPainter.paint(graphics, rect, declarationWidth, lineHeight, hoverColor())
    }
  }

  fun hide() {
    activeTarget = null
    balloonController.hide()
  }

  private fun computeLineHeight(context: MinimapRenderContext): Int {
    val lineCount = panel.editor.document.lineCount
    if (lineCount <= 0) return 1
    val baseLineHeight = MinimapLineGeometryUtil.baseLineHeight(lineCount, context.geometry.minimapHeight)
    val lineGap = MinimapLineGeometryUtil.lineGap(baseLineHeight)

    return MinimapLineGeometryUtil.lineHeight(baseLineHeight, lineGap).roundToInt().coerceAtLeast(1)
  }

  private fun hoverColor(): Color {
    val scheme = panel.editor.colorsScheme
    return scheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)?.foregroundColor
           ?: JBColor.BLUE
  }

  private fun hoverFrameLeftInset(): Int {
    return if (panel.settings.state.rightAligned) JBUI.scale(1) else 0
  }
}

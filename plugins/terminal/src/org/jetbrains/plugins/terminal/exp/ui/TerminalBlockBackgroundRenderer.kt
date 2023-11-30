// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.exp.TerminalUi
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

/**
 * Paints the background of the block, but only in the area of the text (without top, left and bottom corners).
 * So the selection can be painted on top of it.
 */
class TerminalBlockBackgroundRenderer private constructor(
  private val background: Color?,
  private val gradientCache: GradientTextureCache?
) : CustomHighlighterRenderer {
  /** Paints the solid background with provided color */
  constructor(background: Color) : this(background = background, gradientCache = null)

  /** Paints the linear gradient from left to right */
  constructor(gradientCache: GradientTextureCache) : this(background = null, gradientCache = gradientCache)

  override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.BEFORE_BACKGROUND

  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
    val visibleArea = editor.scrollingModel.visibleArea
    val width = visibleArea.width - JBUI.scale(TerminalUi.cornerToBlockInset)
    val topY = editor.offsetToXY(highlighter.startOffset).y.toFloat()
    val bottomY = editor.offsetToXY(highlighter.endOffset).y.toFloat() + editor.lineHeight
    val rect = Rectangle2D.Float(0f, topY, width.toFloat(), bottomY - topY)

    val g2d = g.create() as Graphics2D
    try {
      val paint = gradientCache?.getTexture(g2d, width) ?: background
      g2d.paint = paint
      g2d.fill(rect)
    }
    finally {
      g2d.dispose()
    }
  }
}
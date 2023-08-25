// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.ui.GraphicsUtil
import org.jetbrains.plugins.terminal.exp.TerminalBlocksDecorator
import org.jetbrains.plugins.terminal.exp.TerminalUi
import org.jetbrains.plugins.terminal.exp.TerminalUiUtils.toFloatAndScale
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

/**
 * Paints the gradient background of the block, but only in the area of the text (without top, left and bottom corners).
 * So the selection can be painted on top of it.
 */
class TerminalBlockBackgroundRenderer : CustomHighlighterRenderer {
  override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.BEFORE_BACKGROUND

  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
    val visibleArea = editor.scrollingModel.visibleArea
    val width = visibleArea.width - toFloatAndScale(TerminalUi.cornerToBlockInset)
    val topY = editor.offsetToXY(highlighter.startOffset).y.toFloat()
    val bottomY = editor.offsetToXY(highlighter.endOffset).y.toFloat() + editor.lineHeight
    val rect = Rectangle2D.Float(0f, topY, width, bottomY - topY)

    val g2d = g.create() as Graphics2D
    try {
      GraphicsUtil.setupAntialiasing(g2d)
      g2d.paint = GradientPaint(0f, topY, TerminalUi.blockBackgroundStart,
                                width, topY, TerminalUi.blockBackgroundEnd)
      g2d.fill(rect)
    }
    finally {
      g2d.dispose()
    }
  }
}
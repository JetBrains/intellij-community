// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

/**
 * @author Alexander Lobas
 */
internal class BlockSeparatorRenderer : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
    if (highlighter.endOffset == editor.document.textLength) {
      return
    }

    val visibleArea = editor.scrollingModel.visibleArea
    val rightX = visibleArea.width - JBUI.scale(TerminalUi.blockSeparatorRightOffset).toFloat()
    val bottomY = editor.offsetToXY(highlighter.endOffset).y.toFloat() + editor.lineHeight + JBUI.scale(TerminalUi.blockBottomInset)
    val rect = Rectangle2D.Float(0f, bottomY, rightX, 1f)

    val g2d = g.create() as Graphics2D
    try {
      g2d.color = TerminalUi.promptSeparatorColor(editor)
      g2d.fill(rect)
    }
    finally {
      g2d.dispose()
    }
  }
}
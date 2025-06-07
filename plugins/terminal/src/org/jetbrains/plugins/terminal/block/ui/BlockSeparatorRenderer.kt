// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
class BlockSeparatorRenderer : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
    if (highlighter.startOffset == 0) {
      return  // Do not paint separator if it is the first block
    }

    val visibleArea = editor.scrollingModel.visibleArea
    val width = visibleArea.width - JBUI.scale(TerminalUi.blockSeparatorRightOffset).toFloat()
    val separatorHeight = 1f
    val y = editor.offsetToXY(highlighter.startOffset).y.toFloat() - JBUI.scale(TerminalUi.blockTopInset) - separatorHeight
    val rect = Rectangle2D.Float(0f, y, width, separatorHeight)

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
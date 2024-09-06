// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.terminal.BlockTerminalColors
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints

/**
 * @author Alexander Lobas
 */
internal open class TerminalBlockLeftErrorRenderer : LineMarkerRenderer {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    val gutterWidth = (editor as EditorEx).gutterComponentEx.width
    val x = gutterWidth - JBUI.scale(9)
    val width = JBUI.scale(3)
    val height = r.height - JBUI.scale(TerminalUi.blockBottomInset)
    val arc = JBUI.scale(4)

    val g2d = g.create() as Graphics2D
    try {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2d.color = editor.colorsScheme.getColor(BlockTerminalColors.ERROR_BLOCK_STROKE_COLOR)
      g2d.fillRoundRect(x, r.y, width, height, arc, arc)
    }
    finally {
      g2d.dispose()
    }
  }
}

internal class TerminalBlockLeftErrorRendererWrapper(private val superRenderer: LineMarkerRenderer) : TerminalBlockLeftErrorRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    superRenderer.paint(editor, g, r)
    super.paint(editor, g, r)
  }
}
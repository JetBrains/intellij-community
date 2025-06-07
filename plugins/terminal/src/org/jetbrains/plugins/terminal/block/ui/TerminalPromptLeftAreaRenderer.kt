// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Rectangle2D

@ApiStatus.Internal
class TerminalPromptLeftAreaRenderer : LineMarkerRenderer {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    val topInset = JBUI.scale(TerminalUi.blockTopInset)

    if (r.y == topInset) {
      return  // Do not paint separator if it is the first block
    }

    val gutterWidth = (editor as EditorEx).gutterComponentEx.width.toFloat()
    val separatorHeight = 1f
    val y = r.y - JBUI.scale(TerminalUi.blockTopInset) - separatorHeight
    val rect = Rectangle2D.Float(0f, y, gutterWidth, separatorHeight)

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
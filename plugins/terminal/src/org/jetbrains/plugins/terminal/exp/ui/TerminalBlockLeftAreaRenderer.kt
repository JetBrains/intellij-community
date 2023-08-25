// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.util.ui.GraphicsUtil
import org.jetbrains.plugins.terminal.exp.TerminalUi
import org.jetbrains.plugins.terminal.exp.TerminalUiUtils.toFloatAndScale
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

/** Paints the left part of the rounded block frame in the gutter area. */
class TerminalBlockLeftAreaRenderer : LineMarkerRenderer {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    val topIns = toFloatAndScale(TerminalUi.blockTopInset)
    val bottomIns = toFloatAndScale(TerminalUi.blockBottomInset)
    val width = toFloatAndScale(TerminalUi.blockLeftInset)
    val arc = toFloatAndScale(TerminalUi.blockArc)

    val gutterWidth = (editor as EditorEx).gutterComponentEx.width
    val rect = Rectangle2D.Float(gutterWidth - width, r.y - topIns, width, r.height + topIns + bottomIns)

    val path = Path2D.Float(Path2D.WIND_EVEN_ODD).apply {
      moveTo(rect.x, rect.y + topIns)
      quadTo(rect.x, rect.y, rect.x + arc, rect.y)
      lineTo(rect.x + rect.width, rect.y)
      lineTo(rect.x + rect.width, rect.y + rect.height)
      lineTo(rect.x + arc, rect.y + rect.height)
      quadTo(rect.x, rect.y + rect.height, rect.x, rect.y + rect.height - arc)
      lineTo(rect.x, rect.y + topIns)
      closePath()
    }
    val g2d = g.create() as Graphics2D
    try {
      GraphicsUtil.setupAntialiasing(g2d)
      g2d.color = TerminalUi.blockBackgroundStart
      g2d.fill(path)
    }
    finally {
      g2d.dispose()
    }
  }
}
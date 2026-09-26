// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.caret

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.ide.minimap.paint.MinimapCaretPainter
import com.intellij.openapi.editor.Editor
import java.awt.Graphics2D

class MinimapCaretController(
  editor: Editor,
  private val panel: MinimapPanel,
) {
  private val caretPainter = MinimapCaretPainter(editor, panel)
  private var lastCaretOffset: Int = editor.caretModel.primaryCaret.offset

  fun paint(graphics: Graphics2D): Unit = caretPainter.paint(graphics)

  fun caretMoved(newOffset: Int) {
    repaintCaretRectAt(lastCaretOffset)
    repaintCaretRectAt(newOffset)
    lastCaretOffset = newOffset
  }

  private fun repaintCaretRectAt(offset: Int) {
    val rect = caretPainter.caretRectForOffset(offset) ?: return
    panel.repaint(rect.x, rect.y, rect.width, rect.height)
  }
}

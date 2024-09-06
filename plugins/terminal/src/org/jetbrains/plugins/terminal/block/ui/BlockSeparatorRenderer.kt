// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.ui.JBUI
import java.awt.Graphics

/**
 * @author Alexander Lobas
 */
internal class BlockSeparatorRenderer : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
    if (editor.inlayModel.getBlockElementsInRange(highlighter.endOffset, editor.document.textLength).size < 2) {
      return
    }

    val visibleArea = editor.scrollingModel.visibleArea
    val bottomY = editor.offsetToXY(highlighter.endOffset).y + editor.lineHeight + JBUI.scale(TerminalUi.blockBottomInset + 1)

    g.color = TerminalUi.promptSeparatorColor(editor)
    g.drawLine(0, bottomY, visibleArea.width - JBUI.scale(8), bottomY)
  }
}
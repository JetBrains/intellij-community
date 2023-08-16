// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import kotlin.math.ceil
import kotlin.math.floor

class TerminalCaretPainter(private val caretModel: TerminalCaretModel,
                           private val editor: EditorEx) : TerminalCaretModel.CaretListener {
  private var caretHighlighter: RangeHighlighter? = null
  private val caretColor: Color
    get() = editor.colorsScheme.getColor(EditorColors.CARET_COLOR) ?: JBColor(CARET_DARK, CARET_LIGHT)

  init {
    caretModel.addListener(this)
  }

  fun repaint() {
    updateCaretHighlighter(caretModel.caretPosition)
  }

  override fun caretPositionChanged(oldPosition: LogicalPosition?, newPosition: LogicalPosition?) {
    invokeLater {
      if (!editor.isDisposed) {
        updateCaretHighlighter(newPosition)
      }
    }
  }

  private fun updateCaretHighlighter(newPosition: LogicalPosition?) {
    removeHighlighter()
    if (newPosition != null) {
      installCaretHighlighter(newPosition)
    }
  }

  private fun installCaretHighlighter(newPosition: LogicalPosition) {
    val newOffset = editor.logicalPositionToOffset(newPosition)
    val caretForeground = if (ColorUtil.isDark(caretColor)) CARET_LIGHT else CARET_DARK
    val attributes = TextAttributes(caretForeground, null, null, null, Font.PLAIN)
    val endOffset = if (newOffset + 1 < editor.document.textLength) newOffset + 1 else newOffset
    val highlighter = editor.markupModel.addRangeHighlighter(newOffset, endOffset, HighlighterLayer.LAST,
                                                             attributes, HighlighterTargetArea.EXACT_RANGE)
    highlighter.setCustomRenderer { _, _, g ->
      val offset = highlighter.startOffset
      val point = editor.offsetToPoint2D(offset)
      val caretHeight = calculateCaretHeight()
      val caretInset = (editor.lineHeight - caretHeight) / 2
      val rect = Rectangle2D.Double(point.x, point.y + caretInset,
                                    (editor as EditorImpl).charHeight.toDouble(), caretHeight.toDouble())
      g as Graphics2D
      val oldColor = g.color
      try {
        g.color = caretColor
        g.fill(rect)
      }
      finally {
        g.color = oldColor
      }
    }
    caretHighlighter = highlighter
  }

  private fun removeHighlighter() {
    caretHighlighter?.let {
      editor.markupModel.removeHighlighter(it)
    }
    caretHighlighter = null
  }

  /**
   * It would be great to have [com.intellij.openapi.editor.impl.view.EditorView.myTopOverhang]
   * and [com.intellij.openapi.editor.impl.view.EditorView.myBottomOverhang] values here to properly calculate the caret height.
   * But there is no way to access the EditorView.
   * So, it is a custom solution, that can be not accurate in some cases.
   */
  private fun calculateCaretHeight(): Int {
    // get part of the line height as an insets (top + bottom)
    val rawCaretInset = editor.lineHeight * 0.2
    // make sure that inset is even, because we will need to divide it by 2
    val caretInsets = if (floor(rawCaretInset).toInt() % 2 == 0) {
      floor(rawCaretInset).toInt()
    }
    else ceil(rawCaretInset).toInt()
    return editor.lineHeight - caretInsets
  }

  companion object {
    private val CARET_LIGHT: Color = Gray._255
    private val CARET_DARK: Color = Gray._0
  }
}
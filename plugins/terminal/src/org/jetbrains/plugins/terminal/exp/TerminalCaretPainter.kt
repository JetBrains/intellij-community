// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.terminal.exp.TerminalCaretModel.CaretState
import org.jetbrains.plugins.terminal.exp.TerminalSelectionModel.TerminalSelectionListener
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.floor

class TerminalCaretPainter(
  private val caretModel: TerminalCaretModel,
  private val outputModel: TerminalOutputModel,
  selectionModel: TerminalSelectionModel,
  private val editor: EditorEx
) : TerminalCaretModel.CaretListener, FocusChangeListener, TerminalSelectionListener, Disposable {
  private var caretHighlighter: RangeHighlighter? = null
  private var caretUpdater: BlinkingCaretUpdater? = null
  private var isFocused: Boolean = false
  private var isBlockActive: Boolean = true

  @Volatile
  private var isDisposed: Boolean = false

  private val caretColor: Color
    get() = editor.colorsScheme.getColor(EditorColors.CARET_COLOR) ?: JBColor(CARET_DARK, CARET_LIGHT)

  init {
    caretModel.addListener(this, disposable = this)
    selectionModel.addListener(this, disposable = this)
    editor.addFocusListener(this, this)
  }

  override fun dispose() {
    isDisposed = true
    runInEdt {
      updateCaretHighlighter(CaretState(null))
    }
  }

  @RequiresEdt
  fun repaint() {
    updateCaretHighlighter(caretModel.state)
  }

  override fun caretStateChanged(state: CaretState) {
    updateCaretHighlighter(state)
  }

  override fun focusGained(editor: Editor) {
    isFocused = true
    repaint()
  }

  override fun focusLost(editor: Editor) {
    isFocused = false
    updateCaretHighlighter(CaretState(null))
  }

  override fun selectionChanged(oldSelection: List<CommandBlock>, newSelection: List<CommandBlock>) {
    isBlockActive = newSelection.isEmpty() || newSelection.singleOrNull() == outputModel.getLastBlock()
    if (isBlockActive) {
      repaint()
    }
    else updateCaretHighlighter(CaretState(null))
  }

  private fun updateCaretHighlighter(state: CaretState) {
    removeHighlighter()
    caretUpdater?.let { Disposer.dispose(it) }
    caretUpdater = null
    if (state.position != null && isFocused && isBlockActive) {
      installCaretHighlighter(state.position)
      if (state.isBlinking) {
        caretUpdater = BlinkingCaretUpdater(state.position)
        Disposer.register(this, caretUpdater!!)
      }
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

  private inner class BlinkingCaretUpdater(private val position: LogicalPosition) : Disposable {
    private val updateFuture: ScheduledFuture<*>
    private var paintCaret: Boolean = false

    init {
      val period = editor.settings.caretBlinkPeriod.toLong()
      updateFuture = EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(this::update, period, period,
                                                                                              TimeUnit.MILLISECONDS)
    }

    private fun update() {
      if (!editor.isDisposed) {
        removeHighlighter()
        if (!isDisposed && paintCaret) {
          installCaretHighlighter(position)
        }
        paintCaret = !paintCaret
      }
    }

    override fun dispose() {
      updateFuture.cancel(false)
    }
  }

  companion object {
    private val CARET_LIGHT: Color = Gray._255
    private val CARET_DARK: Color = Gray._0
  }
}
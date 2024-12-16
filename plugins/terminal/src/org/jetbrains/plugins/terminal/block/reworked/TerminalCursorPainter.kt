// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.terminal.CursorShape
import kotlinx.coroutines.*
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import kotlin.math.ceil
import kotlin.math.floor

internal class TerminalCursorPainter private constructor(
  private val outputModel: TerminalOutputModel,
  private val sessionModel: TerminalSessionModel,
  private val coroutineScope: CoroutineScope,
) {
  private val editor: EditorEx
    get() = outputModel.editor
  private val cursorColor: Color
    get() = editor.colorsScheme.getColor(EditorColors.CARET_COLOR) ?: JBColor(CURSOR_DARK, CURSOR_LIGHT)

  private var cursorPaintingJob: Job? = null

  private var curCursorState: CursorState = CursorState(
    offset = outputModel.cursorOffsetState.value,
    isFocused = editor.contentComponent.hasFocus(),
    isCursorVisible = sessionModel.terminalState.value.isCursorVisible,
    cursorShape = sessionModel.terminalState.value.cursorShape
  )

  init {
    updateCursor(curCursorState)

    coroutineScope.launch(Dispatchers.EDT) {
      outputModel.cursorOffsetState.collect { offset ->
        curCursorState = curCursorState.copy(offset = offset)
        updateCursor(curCursorState)
      }
    }

    coroutineScope.launch(Dispatchers.EDT) {
      var cursorShape = curCursorState.cursorShape
      sessionModel.terminalState.collect { state ->
        if (state.cursorShape != cursorShape) {
          cursorShape = state.cursorShape

          curCursorState = curCursorState.copy(cursorShape = cursorShape)
          updateCursor(curCursorState)
        }
      }
    }

    editor.addFocusListener(object : FocusChangeListener {
      override fun focusGained(editor: Editor) {
        curCursorState = curCursorState.copy(isFocused = true)
        updateCursor(curCursorState)
      }

      override fun focusLost(editor: Editor) {
        curCursorState = curCursorState.copy(isFocused = false)
        updateCursor(curCursorState)
      }
    }, coroutineScope.asDisposable())
  }

  @RequiresEdt
  private fun updateCursor(state: CursorState) {
    cursorPaintingJob?.cancel()
    cursorPaintingJob = coroutineScope.launch(Dispatchers.EDT, CoroutineStart.UNDISPATCHED) {
      paintCursor(state)
    }
  }

  @RequiresEdt
  private suspend fun paintCursor(state: CursorState) {
    if (!state.isCursorVisible) {
      return
    }

    if (state.isFocused) {
      paintBlinkingCursor(state)
    }
    else {
      paintEmptyCursor(state)
    }
  }

  private suspend fun paintBlinkingCursor(state: CursorState) {
    var highlighter: RangeHighlighter? = installCursorHighlighter(state.offset)
    try {
      val blinkingPeriod = editor.settings.caretBlinkPeriod.toLong()
      var shouldPaint = false

      while (true) {
        delay(blinkingPeriod)

        if (shouldPaint) {
          highlighter = installCursorHighlighter(state.offset)
        }
        else {
          highlighter?.dispose()
          highlighter = null
        }

        shouldPaint = !shouldPaint
      }
    }
    finally {
      highlighter?.dispose()
    }
  }

  private suspend fun paintEmptyCursor(state: CursorState) {
    val highlighter = installCursorHighlighter(state.offset)
    try {
      awaitCancellation()
    }
    finally {
      highlighter.dispose()
    }
  }

  @RequiresEdt
  private fun installCursorHighlighter(newOffset: Int): RangeHighlighter {
    val cursorForeground = if (ColorUtil.isDark(cursorColor)) CURSOR_LIGHT else CURSOR_DARK
    val attributes = TextAttributes(cursorForeground, null, null, null, Font.PLAIN)
    val endOffset = if (newOffset + 1 < editor.document.textLength) newOffset + 1 else newOffset
    val highlighter = editor.markupModel.addRangeHighlighter(newOffset, endOffset, HighlighterLayer.LAST,
                                                             attributes, HighlighterTargetArea.EXACT_RANGE)
    highlighter.setCustomRenderer { _, _, g ->
      val offset = highlighter.startOffset
      val point = editor.offsetToPoint2D(offset)
      val cursorHeight = calculateCursorHeight()
      val cursorInset = (editor.lineHeight - cursorHeight) / 2
      val rect = Rectangle2D.Double(point.x, point.y + cursorInset,
                                    (editor as EditorImpl).charHeight.toDouble(), cursorHeight.toDouble())
      g as Graphics2D
      val oldColor = g.color
      try {
        g.color = cursorColor
        g.fill(rect)
      }
      finally {
        g.color = oldColor
      }
    }

    return highlighter
  }

  /**
   * It would be great to have [com.intellij.openapi.editor.impl.view.EditorView.myTopOverhang]
   * and [com.intellij.openapi.editor.impl.view.EditorView.myBottomOverhang] values here to properly calculate the cursor height.
   * But there is no way to access the EditorView.
   * So, it is a custom solution, that can be not accurate in some cases.
   */
  private fun calculateCursorHeight(): Int {
    // get part of the line height as an insets (top + bottom)
    val rawCursorInset = editor.lineHeight * 0.2
    // make sure that inset is even, because we will need to divide it by 2
    val cursorInsets = if (floor(rawCursorInset).toInt() % 2 == 0) {
      floor(rawCursorInset).toInt()
    }
    else ceil(rawCursorInset).toInt()
    return editor.lineHeight - cursorInsets
  }

  private data class CursorState(
    val offset: Int,
    val isFocused: Boolean,
    val isCursorVisible: Boolean,
    val cursorShape: CursorShape,
  )

  companion object {
    private val CURSOR_LIGHT: Color = Gray._255
    private val CURSOR_DARK: Color = Gray._0

    @RequiresEdt
    fun install(outputModel: TerminalOutputModel, sessionModel: TerminalSessionModel, coroutineScope: CoroutineScope) {
      TerminalCursorPainter(outputModel, sessionModel, coroutineScope)
    }
  }
}
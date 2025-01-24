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

internal class TerminalCursorPainter private constructor(
  private val editor: EditorEx,
  private val outputModel: TerminalOutputModel,
  private val sessionModel: TerminalSessionModel,
  private val coroutineScope: CoroutineScope,
) {
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
      sessionModel.terminalState.collect { state ->
        var stateChanged = false

        if (state.cursorShape != curCursorState.cursorShape) {
          curCursorState = curCursorState.copy(cursorShape = state.cursorShape)
          stateChanged = true
        }
        if (state.isCursorVisible != curCursorState.isCursorVisible) {
          curCursorState = curCursorState.copy(isCursorVisible = state.isCursorVisible)
          stateChanged = true
        }

        if (stateChanged) {
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
      val renderer = BlockCursorRenderer(editor)
      paintBlinkingCursor(renderer, state.offset)
    }
    else {
      val renderer = EmptyBlockCursorRenderer(editor)
      paintStaticCursor(renderer, state.offset)
    }
  }

  private suspend fun paintBlinkingCursor(renderer: CursorRenderer, offset: Int) {
    var highlighter: RangeHighlighter? = renderer.installCursorHighlighter(offset)
    try {
      val blinkingPeriod = editor.settings.caretBlinkPeriod.toLong()
      var shouldPaint = false

      while (true) {
        delay(blinkingPeriod)

        if (shouldPaint) {
          highlighter = renderer.installCursorHighlighter(offset)
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

  private suspend fun paintStaticCursor(renderer: CursorRenderer, offset: Int) {
    val highlighter = renderer.installCursorHighlighter(offset)
    try {
      awaitCancellation()
    }
    finally {
      highlighter.dispose()
    }
  }

  private data class CursorState(
    val offset: Int,
    val isFocused: Boolean,
    val isCursorVisible: Boolean,
    val cursorShape: CursorShape,
  )

  private sealed interface CursorRenderer {
    @RequiresEdt
    fun installCursorHighlighter(offset: Int): RangeHighlighter
  }

  private sealed class CursorRendererBase(private val editor: EditorEx) : CursorRenderer {
    val editorCursorColor: Color
      get() = editor.colorsScheme.getColor(EditorColors.CARET_COLOR) ?: JBColor(CURSOR_DARK, CURSOR_LIGHT)

    abstract val cursorForeground: Color?

    abstract fun paintCursor(g: Graphics2D, rect: Rectangle2D.Double)

    final override fun installCursorHighlighter(offset: Int): RangeHighlighter {
      val attributes = TextAttributes(cursorForeground, null, null, null, Font.PLAIN)
      val endOffset = if (offset + 1 < editor.document.textLength) offset + 1 else offset
      val highlighter = editor.markupModel.addRangeHighlighter(offset, endOffset, HighlighterLayer.LAST,
                                                               attributes, HighlighterTargetArea.EXACT_RANGE)
      highlighter.setCustomRenderer { _, _, g ->
        val offset = highlighter.startOffset
        val point = editor.offsetToPoint2D(offset)
        val cursorHeight = editor.lineHeight
        val rect = Rectangle2D.Double(point.x, point.y,
                                      (editor as EditorImpl).charHeight.toDouble(), cursorHeight.toDouble())
        g as Graphics2D
        paintCursor(g, rect)
      }

      return highlighter
    }
  }

  private class BlockCursorRenderer(editor: EditorEx) : CursorRendererBase(editor) {
    override val cursorForeground: Color
      get() = if (ColorUtil.isDark(editorCursorColor)) CURSOR_LIGHT else CURSOR_DARK

    override fun paintCursor(g: Graphics2D, rect: Rectangle2D.Double) {
      val oldColor = g.color
      try {
        g.color = editorCursorColor
        g.fill(rect)
      }
      finally {
        g.color = oldColor
      }
    }
  }

  private class EmptyBlockCursorRenderer(editor: EditorEx) : CursorRendererBase(editor) {
    override val cursorForeground: Color? = null

    override fun paintCursor(g: Graphics2D, rect: Rectangle2D.Double) {
      val oldColor = g.color
      try {
        g.color = editorCursorColor
        g.draw(rect)
      }
      finally {
        g.color = oldColor
      }
    }
  }

  companion object {
    private val CURSOR_LIGHT: Color = Gray._255
    private val CURSOR_DARK: Color = Gray._0

    @RequiresEdt
    fun install(editor: EditorEx, outputModel: TerminalOutputModel, sessionModel: TerminalSessionModel, coroutineScope: CoroutineScope) {
      TerminalCursorPainter(editor, outputModel, sessionModel, coroutineScope)
    }
  }
}
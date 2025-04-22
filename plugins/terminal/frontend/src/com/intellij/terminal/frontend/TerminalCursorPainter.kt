// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.codePointAt
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.CharacterGrid
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.terminal.CursorShape
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.util.concurrent.CopyOnWriteArrayList

internal class TerminalCursorPainter private constructor(
  private val editor: EditorEx,
  private val outputModel: TerminalOutputModel,
  private val sessionModel: TerminalSessionModel,
  private val coroutineScope: CoroutineScope,
) {
  private val listeners = CopyOnWriteArrayList<TerminalCursorPainterListener>()

  private var cursorPaintingJob: Job? = null

  private var curCursorState: CursorState = CursorState(
    offset = outputModel.cursorOffsetState.value,
    isFocused = editor.contentComponent.hasFocus(),
    isCursorVisible = sessionModel.terminalState.value.isCursorVisible,
    cursorShape = sessionModel.terminalState.value.cursorShape,
  )

  init {
    updateCursor(curCursorState)

    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      outputModel.cursorOffsetState.collect { offset ->
        curCursorState = curCursorState.copy(offset = offset)
        updateCursor(curCursorState)
      }
    }

    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
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

    // Handling the case when:
    // 0. The cursor is at the end of the document.
    // 1. Something was appended to the document.
    // 2. An equal amount of text was removed from the beginning, so that the max document size is maintained.
    // 3. As a result, the logical offset of the cursor stayed the same, but we still need to repaint it.
    outputModel.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
      override fun afterContentChanged(startOffset: Int) {
        // This listener exists to handle the case when the offset has not changed,
        // but it must also work correctly when the offset has in fact changed.
        // In that case, the offset is updated before this listener is invoked,
        // but it may not have been collected yet,
        // because the collector might be called in an invokeLater by the coroutine dispatcher.
        // Therefore, the flow is guaranteed to have the correct value, but curCursorState is not.
        curCursorState = curCursorState.copy(offset = outputModel.cursorOffsetState.value)
        updateCursor(curCursorState)
      }
    })
  }

  fun addListener(parentDisposable: Disposable, listener: TerminalCursorPainterListener) {
    TerminalUtil.addItem(listeners, listener, parentDisposable)
  }

  @RequiresEdt
  private fun updateCursor(state: CursorState) {
    cursorPaintingJob?.cancel()
    cursorPaintingJob = coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement(), CoroutineStart.UNDISPATCHED) {
      paintCursor(state)
    }
  }

  @RequiresEdt
  private suspend fun paintCursor(state: CursorState) {
    if (!state.isCursorVisible) {
      return
    }

    val cursorShape = state.cursorShape ?: getDefaultCursorShape()
    val shouldBlink = state.isFocused && cursorShape.isBlinking
    val renderer = when (cursorShape) {
      CursorShape.BLINK_BLOCK, CursorShape.STEADY_BLOCK ->
        if (state.isFocused) {
          BlockCursorRenderer(editor, listeners)
        }
        else {
          EmptyBlockCursorRenderer(editor, listeners)
        }
      CursorShape.BLINK_UNDERLINE, CursorShape.STEADY_UNDERLINE -> UnderlineCursorRenderer(editor, listeners)
      CursorShape.BLINK_VERTICAL_BAR, CursorShape.STEADY_VERTICAL_BAR -> VerticalBarCursorRenderer(editor, listeners)
    }
    if (shouldBlink) {
      paintBlinkingCursor(renderer, state.offset)
    }
    else {
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

  private fun getDefaultCursorShape(): CursorShape {
    val editorSettings = editor.settings
    val shapeFromSettings = TerminalOptionsProvider.instance.cursorShape
    when (shapeFromSettings) {
      TerminalUiSettingsManager.CursorShape.BLOCK -> {
        return if (editorSettings.isBlinkCaret) CursorShape.BLINK_BLOCK else CursorShape.STEADY_BLOCK
      }
      TerminalUiSettingsManager.CursorShape.UNDERLINE -> {
        return if (editorSettings.isBlinkCaret) CursorShape.BLINK_UNDERLINE else CursorShape.STEADY_UNDERLINE
      }
      else -> return if (editorSettings.isBlinkCaret) CursorShape.BLINK_VERTICAL_BAR else CursorShape.STEADY_VERTICAL_BAR
    }
  }

  private data class CursorState(
    val offset: Int,
    val isFocused: Boolean,
    val isCursorVisible: Boolean,
    val cursorShape: CursorShape?,
  )

  private sealed interface CursorRenderer {
    @RequiresEdt
    fun installCursorHighlighter(offset: Int): RangeHighlighter
  }

  private sealed class CursorRendererBase(
    private val editor: EditorEx,
    private val listeners: List<TerminalCursorPainterListener>,
  ) : CursorRenderer {
    private val grid: CharacterGrid = requireNotNull((editor as? EditorImpl)?.characterGrid) { "The editor is not in the grid mode" }

    val editorCursorColor: Color
      get() = editor.colorsScheme.getColor(EditorColors.CARET_COLOR) ?: JBColor(CURSOR_DARK, CURSOR_LIGHT)

    abstract val cursorForeground: Color?

    abstract fun paintCursor(g: Graphics2D, rect: Rectangle2D.Double)

    protected inline fun Graphics2D.withCursorColor(block: () -> Unit) {
      val oldColor = color
      try {
        color = editorCursorColor
        block()
      }
      finally {
        color = oldColor
      }
    }

    final override fun installCursorHighlighter(offset: Int): RangeHighlighter {
      val attributes = TextAttributes(cursorForeground, null, null, null, Font.PLAIN)
      // offset == textLength is allowed (it means that the cursor is at the end, a very common case)
      val startOffset = offset.coerceIn(0..editor.document.textLength)
      val endOffset = (offset + 1).coerceIn(0..editor.document.textLength)
      val highlighter = editor.markupModel.addRangeHighlighter(startOffset, endOffset, HighlighterLayer.LAST,
                                                               attributes, HighlighterTargetArea.EXACT_RANGE)
      highlighter.setCustomRenderer { _, _, g ->
        val offset = highlighter.startOffset
        val point = editor.offsetToPoint2D(offset)
        val text = editor.document.immutableCharSequence
        val codePoint = if (offset in text.indices) text.codePointAt(offset) else 'W'.code
        val cursorWidth = grid.codePointWidth(codePoint)
        val cursorHeight = editor.lineHeight
        val rect = Rectangle2D.Double(point.x, point.y, cursorWidth.toDouble(), cursorHeight.toDouble())
        g as Graphics2D
        paintCursor(g, rect)

        for (listener in listeners) {
          listener.cursorPainted()
        }
      }

      return highlighter
    }
  }

  private class BlockCursorRenderer(
    editor: EditorEx,
    listeners: List<TerminalCursorPainterListener>,
  ) : CursorRendererBase(editor, listeners) {
    override val cursorForeground: Color
      get() = if (ColorUtil.isDark(editorCursorColor)) CURSOR_LIGHT else CURSOR_DARK

    override fun paintCursor(g: Graphics2D, rect: Rectangle2D.Double) {
      g.withCursorColor {
        g.fill(rect)
      }
    }
  }

  private class EmptyBlockCursorRenderer(
    editor: EditorEx,
    listeners: List<TerminalCursorPainterListener>,
  ) : CursorRendererBase(editor, listeners) {
    override val cursorForeground: Color? = null

    override fun paintCursor(g: Graphics2D, rect: Rectangle2D.Double) {
      g.withCursorColor {
        g.draw(rect)
      }
    }
  }

  private abstract class LineCursorRenderer(
    editor: EditorEx,
    listeners: List<TerminalCursorPainterListener>,
  ) : CursorRendererBase(editor, listeners) {
    override val cursorForeground: Color? = null

    protected val lineThickness: Double get() = JBUIScale.scale(2.0f).toDouble()

    protected abstract fun shape(rect: Rectangle2D.Double): Rectangle2D.Double

    override fun paintCursor(g: Graphics2D, rect: Rectangle2D.Double) {
      g.withCursorColor {
        g.fill(shape(rect))
      }
    }
  }

  private class UnderlineCursorRenderer(
    editor: EditorEx,
    listeners: List<TerminalCursorPainterListener>,
  ) : LineCursorRenderer(editor, listeners) {
    override fun shape(rect: Rectangle2D.Double): Rectangle2D.Double =
      Rectangle2D.Double(rect.x, rect.y + rect.height - lineThickness, rect.width, lineThickness)
  }

  private class VerticalBarCursorRenderer(
    editor: EditorEx,
    listeners: List<TerminalCursorPainterListener>,
  ) : LineCursorRenderer(editor, listeners) {
    override fun shape(rect: Rectangle2D.Double): Rectangle2D.Double =
      Rectangle2D.Double(rect.x, rect.y, lineThickness, rect.height)
  }

  companion object {
    private val CURSOR_LIGHT: Color = Gray._255
    private val CURSOR_DARK: Color = Gray._0

    @RequiresEdt
    fun install(
      editor: EditorEx,
      outputModel: TerminalOutputModel,
      sessionModel: TerminalSessionModel,
      coroutineScope: CoroutineScope,
    ): TerminalCursorPainter {
      return TerminalCursorPainter(editor, outputModel, sessionModel, coroutineScope)
    }
  }
}
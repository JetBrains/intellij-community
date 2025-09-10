// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.codePointAt
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.CharacterGrid
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.ui.AwtTransformers
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.block.reworked.TerminalOffset
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.ui.BlockTerminalColorPalette
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
          BlockCursorRenderer(editor, outputModel, listeners)
        }
        else {
          EmptyBlockCursorRenderer(editor, outputModel, listeners)
        }
      CursorShape.BLINK_UNDERLINE, CursorShape.STEADY_UNDERLINE -> UnderlineCursorRenderer(editor, outputModel, listeners)
      CursorShape.BLINK_VERTICAL_BAR, CursorShape.STEADY_VERTICAL_BAR -> VerticalBarCursorRenderer(editor, outputModel, listeners)
    }

    val documentOffset = state.offset.toRelative()
    if (shouldBlink) {
      paintBlinkingCursor(renderer, documentOffset)
    }
    else {
      paintStaticCursor(renderer, documentOffset)
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
    val offset: TerminalOffset,
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
    private val outputModel: TerminalOutputModel,
    private val listeners: List<TerminalCursorPainterListener>,
  ) : CursorRenderer {
    private val grid: CharacterGrid = requireNotNull((editor as? EditorImpl)?.characterGrid) { "The editor is not in the grid mode" }

    /** Whether background color should be used as a foreground for text under the cursor */
    open val inverseForeground: Boolean = false

    abstract fun paintCursor(g: Graphics2D, rect: Rectangle2D.Double, color: Color)

    protected inline fun Graphics2D.withColor(color: Color, block: () -> Unit) {
      val oldColor = color
      try {
        this.color = color
        block()
      }
      finally {
        this.color = oldColor
      }
    }

    final override fun installCursorHighlighter(offset: Int): RangeHighlighter {
      val cursorAttributes = getCursorTextAttributes(offset)
      val colorPalette = BlockTerminalColorPalette()
      val foregroundColor = cursorAttributes.foregroundColor ?: AwtTransformers.toAwtColor(colorPalette.defaultForeground)!!
      val backgroundColor = cursorAttributes.backgroundColor ?: AwtTransformers.toAwtColor(colorPalette.defaultBackground)!!

      val effectiveForeground = if (inverseForeground) backgroundColor else foregroundColor
      val attributes = TextAttributes(effectiveForeground, null, null, null, Font.PLAIN)

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
        paintCursor(g, rect, foregroundColor)

        for (listener in listeners) {
          listener.cursorPainted()
        }
      }

      return highlighter
    }

    private fun getCursorTextAttributes(offset: Int): TextAttributes {
      val highlighting = outputModel.getHighlightingAt(offset)
      return highlighting?.textAttributesProvider?.getTextAttributes()
             ?: TextAttributes.ERASE_MARKER // If there are no specific highlighting, use the default
    }
  }

  private class BlockCursorRenderer(
    editor: EditorEx,
    outputModel: TerminalOutputModel,
    listeners: List<TerminalCursorPainterListener>,
  ) : CursorRendererBase(editor, outputModel, listeners) {
    override val inverseForeground: Boolean = true

    override fun paintCursor(g: Graphics2D, rect: Rectangle2D.Double, color: Color) {
      g.withColor(color) {
        g.fill(rect)
      }
    }
  }

  private class EmptyBlockCursorRenderer(
    editor: EditorEx,
    outputModel: TerminalOutputModel,
    listeners: List<TerminalCursorPainterListener>,
  ) : CursorRendererBase(editor, outputModel, listeners) {
    override fun paintCursor(g: Graphics2D, rect: Rectangle2D.Double, color: Color) {
      g.withColor(color) {
        g.draw(rect)
      }
    }
  }

  private abstract class LineCursorRenderer(
    editor: EditorEx,
    outputModel: TerminalOutputModel,
    listeners: List<TerminalCursorPainterListener>,
  ) : CursorRendererBase(editor, outputModel, listeners) {
    protected val lineThickness: Double get() = JBUIScale.scale(2.0f).toDouble()

    protected abstract fun shape(rect: Rectangle2D.Double): Rectangle2D.Double

    override fun paintCursor(g: Graphics2D, rect: Rectangle2D.Double, color: Color) {
      g.withColor(color) {
        g.fill(shape(rect))
      }
    }
  }

  private class UnderlineCursorRenderer(
    editor: EditorEx,
    outputModel: TerminalOutputModel,
    listeners: List<TerminalCursorPainterListener>,
  ) : LineCursorRenderer(editor, outputModel, listeners) {
    override fun shape(rect: Rectangle2D.Double): Rectangle2D.Double =
      Rectangle2D.Double(rect.x, rect.y + rect.height - lineThickness, rect.width, lineThickness)
  }

  private class VerticalBarCursorRenderer(
    editor: EditorEx,
    outputModel: TerminalOutputModel,
    listeners: List<TerminalCursorPainterListener>,
  ) : LineCursorRenderer(editor, outputModel, listeners) {
    override fun shape(rect: Rectangle2D.Double): Rectangle2D.Double =
      Rectangle2D.Double(rect.x, rect.y, lineThickness, rect.height)
  }

  companion object {
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
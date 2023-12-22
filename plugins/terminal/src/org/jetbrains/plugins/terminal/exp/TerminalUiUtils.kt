// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorGutterFreePainterAreaState
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalColorPalette
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.ui.AwtTransformers
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.CompletableFuture
import javax.swing.JScrollPane
import kotlin.math.max

object TerminalUiUtils {
  fun createOutputEditor(document: Document, project: Project, settings: JBTerminalSystemSettingsProviderBase): EditorImpl {
    val editor = EditorFactory.getInstance().createEditor(document, project, EditorKind.CONSOLE) as EditorImpl
    editor.isScrollToCaret = false
    editor.isRendererMode = true
    editor.setCustomCursor(this, Cursor.getDefaultCursor())
    editor.scrollPane.border = JBUI.Borders.empty()
    editor.scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    editor.gutterComponentEx.isPaintBackground = false
    editor.gutterComponentEx.setRightFreePaintersAreaState(EditorGutterFreePainterAreaState.HIDE)

    editor.colorsScheme.apply {
      editorFontName = settings.terminalFont.fontName
      editorFontSize = settings.terminalFont.size
      lineSpacing = 1.0f
    }

    editor.settings.apply {
      isShowingSpecialChars = false
      isLineNumbersShown = false
      setGutterIconsShown(false)
      isRightMarginShown = false
      isFoldingOutlineShown = false
      isCaretRowShown = false
      additionalLinesCount = 0
      additionalColumnsCount = 0
      isAdditionalPageAtBottom = false
      isBlockCursor = true
      isWhitespacesShown = false
    }

    editor.contextMenuGroupId = "Terminal.OutputContextMenu"
    return editor
  }

  fun calculateTerminalSize(componentSize: Dimension, charSize: Dimension): TermSize {
    val width = componentSize.width / charSize.width
    val height = componentSize.height / charSize.height
    return ensureTermMinimumSize(TermSize(width, height))
  }

  private fun ensureTermMinimumSize(size: TermSize): TermSize {
    return TermSize(max(TerminalModel.MIN_WIDTH, size.columns), max(TerminalModel.MIN_HEIGHT, size.rows))
  }

  @RequiresEdt(generateAssertion = false)
  fun awaitComponentLayout(component: Component, parentDisposable: Disposable): CompletableFuture<Unit> {
    val size = component.size
    if (size.width > 0 || size.height > 0) {
      return CompletableFuture.completedFuture(Unit)
    }
    if (!UIUtil.isShowing(component, false)) {
      return CompletableFuture.failedFuture(IllegalStateException("component should be showing"))
    }
    val result = CompletableFuture<Unit>()

    val startNano = System.nanoTime()
    val resizeListener: ComponentAdapter = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        if (LOG.isDebugEnabled) {
          LOG.info("Terminal component layout took " + TimeoutUtil.getDurationMillis(startNano) + "ms")
        }
        result.complete(Unit)
      }
    }
    component.addComponentListener(resizeListener)

    val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)
    alarm.addRequest({
                       result.completeExceptionally(IllegalStateException("Terminal component layout is timed out (>${TIMEOUT}ms)"))
                     }, TIMEOUT, ModalityState.stateForComponent(component))

    Disposer.register(alarm) {
      if (!result.isDone) {
        ApplicationManager.getApplication().invokeLater({
                                                          result.completeExceptionally(IllegalStateException("parent disposed"))
                                                        }, ModalityState.stateForComponent(component))
      }
    }
    result.whenComplete { _, _ ->
      Disposer.dispose(alarm)
      component.removeComponentListener(resizeListener)
    }

    return result
  }

  fun toFloatAndScale(value: Int): Float = JBUIScale.scale(value.toFloat())

  internal fun TextStyle.toTextAttributes(palette: TerminalColorPalette): TextAttributes {
    return TextAttributes().also { attr ->
      backgroundForRun?.let {
        // [TerminalColorPalette.getDefaultBackground] is not applied to [TextAttributes].
        // It's passed to [EditorEx.setBackgroundColor] / [JComponent.setBackground] to
        // paint the background uniformly.
        attr.backgroundColor = AwtTransformers.toAwtColor(palette.getBackground(it))
      }
      attr.foregroundColor = getForegroundColor(this, palette)
      if (hasOption(TextStyle.Option.BOLD)) {
        attr.fontType = attr.fontType or Font.BOLD
      }
      if (hasOption(TextStyle.Option.ITALIC)) {
        attr.fontType = attr.fontType or Font.ITALIC
      }
      if (hasOption(TextStyle.Option.UNDERLINED)) {
        attr.withAdditionalEffect(EffectType.LINE_UNDERSCORE, attr.foregroundColor)
      }
    }
  }

  private fun getForegroundColor(style: TextStyle, palette: TerminalColorPalette): Color {
    val foreground = getEffectiveForegroundColor(style.foregroundForRun, palette)
    return if (style.hasOption(TextStyle.Option.DIM)) {
      val background = getEffectiveBackgroundColor(style.backgroundForRun, palette)
      @Suppress("UseJBColor")
      Color((foreground.red + background.red) / 2,
            (foreground.green + background.green) / 2,
            (foreground.blue + background.blue) / 2,
            foreground.alpha)
    }
    else foreground
  }

  private fun getEffectiveForegroundColor(color: TerminalColor?, palette: TerminalColorPalette): Color {
    return AwtTransformers.toAwtColor(color?.let { palette.getForeground(it) } ?: palette.defaultForeground)!!
  }

  private fun getEffectiveBackgroundColor(color: TerminalColor?, palette: TerminalColorPalette): Color {
    return AwtTransformers.toAwtColor(color?.let { palette.getBackground(it) } ?: palette.defaultBackground)!!
  }

  private val LOG = logger<TerminalUiUtils>()
  private const val TIMEOUT = 2000
}
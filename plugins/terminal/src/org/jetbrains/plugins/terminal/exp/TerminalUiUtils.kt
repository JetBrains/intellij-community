// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.HyperlinkWithPopupMenuInfo
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterFreePainterAreaState
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
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
import com.intellij.util.asSafely
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.nullize
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.ui.AwtTransformers
import org.intellij.lang.annotations.MagicConstant
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.CompletableFuture
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import kotlin.math.max

object TerminalUiUtils {
  fun createOutputEditor(document: Document, project: Project, settings: JBTerminalSystemSettingsProviderBase): EditorImpl {
    val editor = EditorFactory.getInstance().createEditor(document, project, EditorKind.CONSOLE) as EditorImpl
    editor.isScrollToCaret = false
    editor.isRendererMode = true
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

    installPopupMenu(editor)
    return editor
  }

  private fun installPopupMenu(editor: EditorEx) {
    editor.installPopupHandler(object : ContextMenuPopupHandler() {
      override fun getActionGroup(event: EditorMouseEvent): ActionGroup = getPopupMenuGroup(editor, event)
    })
  }

  private fun getPopupMenuGroup(editor: EditorEx, event: EditorMouseEvent): ActionGroup {
    ThreadingAssertions.assertEventDispatchThread()
    val info: HyperlinkInfo? = EditorHyperlinkSupport.get(editor).getHyperlinkInfoByEvent(event)
    val customPopupMenuGroup = info.asSafely<HyperlinkWithPopupMenuInfo>()?.getPopupMenuGroup(event.mouseEvent)
    val defaultPopupMenuGroup = ActionManager.getInstance().getAction(TERMINAL_OUTPUT_CONTEXT_MENU) as ActionGroup
    return concatGroups(customPopupMenuGroup, defaultPopupMenuGroup)
  }

  private fun concatGroups(vararg groups: ActionGroup?): ActionGroup {
    val actionsPerGroup = groups.mapNotNull {
      it?.getChildren(null).orEmpty().toList().nullize()
    }
    return DefaultActionGroup(actionsPerGroup.flatMapIndexed { index, actions ->
      if (index > 0) listOf(Separator.create()) + actions else actions
    })
  }

  fun createSingleShortcutSet(@MagicConstant(flagsFromClass = KeyEvent::class) keyCode: Int,
                              @MagicConstant(flagsFromClass = InputEvent::class) modifiers: Int): ShortcutSet {
    val keyStroke = KeyStroke.getKeyStroke(keyCode, modifiers)
    return CustomShortcutSet(keyStroke)
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

  fun TerminalColorPalette.getAwtForegroundByIndex(colorIndex: Int): Color {
    val color = if (colorIndex in 0..15) {
      getForeground(TerminalColor.index(colorIndex))
    }
    else defaultForeground
    return AwtTransformers.toAwtColor(color)!!
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

  fun plainAttributesProvider(foregroundColorIndex: Int, palette: TerminalColorPalette): TextAttributesProvider {
    return TextStyleAdapter(TextStyle(TerminalColor(foregroundColorIndex), null), palette)
  }

  private val LOG = logger<TerminalUiUtils>()
  private const val TIMEOUT = 2000
  private const val TERMINAL_OUTPUT_CONTEXT_MENU = "Terminal.OutputContextMenu"

  const val GREEN_COLOR_INDEX: Int = 2
  const val YELLOW_COLOR_INDEX: Int = 3
}
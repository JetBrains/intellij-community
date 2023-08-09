// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jediterm.terminal.StyledTextConsumer
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.emulator.ColorPalette
import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.ui.AwtTransformers
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.Border
import kotlin.math.max

class TerminalPanel(project: Project,
                    private val settings: JBTerminalSystemSettingsProviderBase,
                    private val model: TerminalModel,
                    eventsHandler: TerminalEventsHandler) : JPanel(), ComponentContainer {
  private val document: Document
  private val editor: EditorImpl

  // disposable for updating content and forwarding mouse events
  private val runningDisposable: Disposable = Disposer.newDisposable()
  private val keyEventsForwardingDisposable = Disposer.newDisposable()

  private val palette: ColorPalette
    get() = settings.terminalColorPalette

  val charSize: Dimension
    get() = Dimension(editor.charHeight, editor.lineHeight)

  init {
    document = DocumentImpl("", true)
    editor = TerminalUiUtils.createEditor(document, project, settings)
    Disposer.register(this) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    Disposer.register(this, runningDisposable)
    Disposer.register(this, keyEventsForwardingDisposable)

    setupContentListener()
    setupKeyEventDispatcher(editor, settings, eventsHandler, keyEventsForwardingDisposable, this::isFocused)
    setupMouseListener(editor, settings, model, eventsHandler, runningDisposable)

    border = createBorder(isFullScreen = false)

    layout = BorderLayout()
    add(editor.component, BorderLayout.CENTER)

    updateEditorContent()
  }

  private fun createBorder(isFullScreen: Boolean): Border {
    return if (!isFullScreen) {
      val innerBorder = JBUI.Borders.customLine(UIUtil.getTextFieldBackground(), 6, 0, 6, 0)
      val outerBorder = JBUI.Borders.customLineTop(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
      JBUI.Borders.compound(outerBorder, innerBorder)!!
    }
    else JBUI.Borders.empty()
  }

  private fun setupContentListener() {
    model.addContentListener(object : TerminalModel.ContentListener {
      override fun onContentChanged() {
        updateEditorContent()
      }
    }, runningDisposable)
  }

  private fun updateEditorContent() {
    val content: TerminalContent = computeTerminalContent()
    // Can not use invokeAndWait here because deadlock may happen. TerminalTextBuffer is locked at this place,
    // and EDT can be frozen now trying to acquire this lock
    invokeLater(ModalityState.any()) {
      if (!editor.isDisposed) {
        updateEditor(content)
      }
    }
  }

  private fun computeTerminalContent(): TerminalContent {
    val builder = StringBuilder()
    val highlightings = mutableListOf<HighlightingInfo>()
    val consumer = object : StyledTextConsumer {
      override fun consume(x: Int,
                           y: Int,
                           style: TextStyle,
                           characters: CharBuffer,
                           startRow: Int) {
        val startOffset = builder.length
        builder.append(characters.toString())
        val attributes = style.toTextAttributes()
        highlightings.add(HighlightingInfo(startOffset, builder.length, attributes))
      }

      override fun consumeNul(x: Int,
                              y: Int,
                              nulIndex: Int,
                              style: TextStyle,
                              characters: CharBuffer,
                              startRow: Int) {
        val startOffset = builder.length
        repeat(characters.buf.size) {
          builder.append(' ')
        }
        highlightings.add(HighlightingInfo(startOffset, builder.length, TextStyle.EMPTY.toTextAttributes()))
      }

      override fun consumeQueue(x: Int, y: Int, nulIndex: Int, startRow: Int) {
        builder.append("\n")
        highlightings.add(HighlightingInfo(builder.length - 1, builder.length, TextStyle.EMPTY.toTextAttributes()))
      }
    }

    if (model.useAlternateBuffer) {
      model.processScreenLines(0, model.screenLinesCount, consumer)
    }
    else {
      model.processHistoryAndScreenLines(-model.historyLinesCount, model.historyLinesCount + model.cursorY, consumer)
    }

    while (builder.lastOrNull() == '\n') {
      builder.deleteCharAt(builder.lastIndex)
      highlightings.removeLast()
    }
    return TerminalContent(builder.toString(), highlightings)
  }

  private fun updateEditor(content: TerminalContent) {
    document.setText(content.text)
    editor.highlighter = TerminalHighlighter(content.highlightings)
    if (model.useAlternateBuffer) {
      editor.setCaretEnabled(false)
    }
    else {
      editor.setCaretEnabled(model.isCursorVisible)
      val line = model.historyLinesCount + model.cursorY - 1
      editor.caretModel.moveToLogicalPosition(LogicalPosition(line, model.cursorX))
      editor.scrollingModel.scrollToCaret(ScrollType.CENTER_DOWN)
    }
  }

  private fun TextStyle.toTextAttributes(): TextAttributes {
    return TextAttributes().also { attr ->
      attr.backgroundColor = AwtTransformers.toAwtColor(palette.getBackground(model.styleState.getBackground(backgroundForRun)))
      attr.foregroundColor = getStyleForeground(this)
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

  private fun getStyleForeground(style: TextStyle): Color {
    val foreground = palette.getForeground(model.styleState.getForeground(style.foregroundForRun))
    return if (style.hasOption(TextStyle.Option.DIM)) {
      val background = palette.getBackground(model.styleState.getBackground(style.backgroundForRun))
      Color((foreground.red + background.red) / 2,
            (foreground.green + background.green) / 2,
            (foreground.blue + background.blue) / 2,
            foreground.alpha)
    }
    else AwtTransformers.toAwtColor(foreground)!!
  }

  fun isFocused(): Boolean = editor.contentComponent.hasFocus()

  override fun getPreferredSize(): Dimension {
    val baseSize = super.getPreferredSize()
    JBInsets.addTo(baseSize, insets)
    val lineCount = max(editor.document.lineCount, 1)
    return Dimension(baseSize.width, lineCount * editor.lineHeight + insets.top + insets.bottom)
  }

  fun getContentSize(): Dimension {
    return Dimension(width - JBUI.scale(LEFT_INSET), height)
  }

  override fun getComponent(): JComponent = this

  override fun getPreferredFocusableComponent(): JComponent = editor.contentComponent

  override fun dispose() {
  }

  private data class TerminalContent(val text: String, val highlightings: List<HighlightingInfo>)

  companion object {
    const val LEFT_INSET: Int = 7
  }
}
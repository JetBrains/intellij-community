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
import com.intellij.util.ui.JBUI
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
import javax.swing.JScrollPane

class TerminalPanel(private val project: Project,
                    private val settings: JBTerminalSystemSettingsProviderBase,
                    private val model: TerminalModel,
                    eventsHandler: TerminalEventsHandler,
                    private val withVerticalScroll: Boolean = true) : JPanel(), ComponentContainer {
  private val editor: EditorImpl

  private val document: Document
    get() = editor.document

  // disposable for updating content and forwarding mouse events
  private val runningDisposable: Disposable = Disposer.newDisposable()
  private val keyEventsForwardingDisposable = Disposer.newDisposable()

  private val palette: ColorPalette
    get() = settings.terminalColorPalette

  val terminalWidth: Int
    get() {
      val visibleArea = editor.scrollingModel.visibleArea
      val scrollBarWidth = editor.scrollPane.verticalScrollBar.width
      return visibleArea.width - scrollBarWidth
    }

  val charSize: Dimension
    get() = Dimension(editor.charHeight, editor.lineHeight)

  init {
    editor = createEditor()
    Disposer.register(this) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    Disposer.register(this, runningDisposable)
    Disposer.register(this, keyEventsForwardingDisposable)

    setupContentListener()
    setupKeyEventDispatcher(editor, settings, eventsHandler, keyEventsForwardingDisposable, this::isFocused)
    setupMouseListener(editor, settings, model, eventsHandler, runningDisposable)

    border = JBUI.Borders.emptyLeft(TerminalUI.alternateBufferLeftInset)
    layout = BorderLayout()
    add(editor.component, BorderLayout.CENTER)

    model.withContentLock {
      updateEditorContent()
    }
  }

  private fun createEditor(): EditorImpl {
    val document = DocumentImpl("", true)
    val editor = TerminalUiUtils.createOutputEditor(document, project, settings)
    editor.settings.isLineMarkerAreaShown = false
    editor.scrollPane.verticalScrollBarPolicy = if (withVerticalScroll) {
      JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
    }
    else JScrollPane.VERTICAL_SCROLLBAR_NEVER
    return editor
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
    editor.highlighter = TerminalTextHighlighter(content.highlightings)
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

  fun getContentSize(): Dimension {
    return Dimension(width - JBUI.scale(TerminalUI.alternateBufferLeftInset), height)
  }

  override fun getBackground(): Color {
    return TerminalUI.terminalBackground
  }

  override fun getComponent(): JComponent = this

  override fun getPreferredFocusableComponent(): JComponent = editor.contentComponent

  override fun dispose() {
  }

  private data class TerminalContent(val text: String, val highlightings: List<HighlightingInfo>)
}
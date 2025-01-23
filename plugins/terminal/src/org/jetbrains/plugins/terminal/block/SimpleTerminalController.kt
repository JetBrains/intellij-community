// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.terminal.StyledTextConsumer
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer
import org.jetbrains.plugins.terminal.block.output.*
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.session.TerminalModel
import org.jetbrains.plugins.terminal.block.ui.getDisposed
import org.jetbrains.plugins.terminal.block.ui.invokeLater
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils

internal class SimpleTerminalController(
  settings: JBTerminalSystemSettingsProviderBase,
  private val session: BlockTerminalSession,
  private val editor: EditorEx
) : Disposable {
  val document: Document
    get() = editor.document
  private val terminalModel: TerminalModel
    get() = session.model

  private val outputModel: TerminalOutputModel = TerminalOutputModelImpl(editor)
  private val selectionModel = TerminalSelectionModel(outputModel)  // fake model, that won't be changed
  private val caretModel: TerminalCaretModel = TerminalCaretModel(session, outputModel, editor, parentDisposable = this)
  private val caretPainter: TerminalCaretPainter = TerminalCaretPainter(caretModel, outputModel, selectionModel, editor)

  var isFocused: Boolean = false

  init {
    editor.putUserData(TerminalDataContextUtils.IS_ALTERNATE_BUFFER_EDITOR_KEY, true)
    Disposer.register(this, caretPainter)

    // create dummy logical block, that will cover all the output, needed only for caret model
    outputModel.createBlock(command = null, prompt = null, session.model.width)
    terminalModel.isCommandRunning = true

    setupContentListener()
    val eventsHandler = SimpleTerminalEventsHandler(session, settings, outputModel)
    setupKeyEventDispatcher(editor, eventsHandler, disposable = this)
    setupMouseListener(editor, settings, terminalModel, eventsHandler, disposable = this)
    TerminalOutputEditorInputMethodSupport(
      editor,
      sendInputString = { text -> session.terminalOutputStream.sendString(text, true) },
      getCaretPosition = { caretModel.getCaretPosition() }
    ).install(this)
    terminalModel.withContentLock {
      updateEditorContent()
    }
  }

  @RequiresEdt
  fun clearTextSelection() {
    editor.selectionModel.removeSelection()
  }

  private fun setupContentListener() {
    terminalModel.addContentListener(object : TerminalModel.ContentListener {
      override fun onContentChanged() {
        updateEditorContent()
      }
    }, parentDisposable = this)
  }

  private fun updateEditorContent() {
    val content: TextWithHighlightings = computeTerminalContent()
    invokeLater(editor.getDisposed(), ModalityState.any()) {
      updateEditor(content)
    }
  }

  private fun computeTerminalContent(): TextWithHighlightings {
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
        highlightings.add(HighlightingInfo(startOffset, builder.length, style.toTextAttributesProvider()))
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
        highlightings.add(HighlightingInfo(startOffset, builder.length, TextStyle.EMPTY.toTextAttributesProvider()))
      }

      override fun consumeQueue(x: Int, y: Int, nulIndex: Int, startRow: Int) {
        builder.append("\n")
        highlightings.add(HighlightingInfo(builder.length - 1, builder.length, TextStyle.EMPTY.toTextAttributesProvider()))
      }
    }

    if (terminalModel.useAlternateBuffer) {
      terminalModel.processScreenLines(0, terminalModel.screenLinesCount, consumer)
    }
    else {
      terminalModel.processHistoryAndScreenLines(-terminalModel.historyLinesCount,
                                                 terminalModel.historyLinesCount + terminalModel.cursorY,
                                                 consumer)
    }

    while (builder.lastOrNull() == '\n') {
      builder.deleteCharAt(builder.lastIndex)
      highlightings.removeLast()
    }
    return TextWithHighlightings(builder.toString(), highlightings)
  }

  private fun updateEditor(content: TextWithHighlightings) {
    document.setText(content.text)
    editor.highlighter = TerminalTextHighlighter(TerminalOutputHighlightingsSnapshot(editor.document, content.highlightings))

    val line = terminalModel.historyLinesCount + terminalModel.cursorY - 1
    editor.caretModel.moveToLogicalPosition(LogicalPosition(line, terminalModel.cursorX))
    editor.scrollingModel.scrollToCaret(ScrollType.CENTER_DOWN)
    caretPainter.repaint()
  }

  private fun TextStyle.toTextAttributesProvider(): TextAttributesProvider = TextStyleAdapter(this, session.colorPalette)

  override fun dispose() {}

  companion object {
    val KEY: DataKey<SimpleTerminalController> = DataKey.create("SimpleTerminalController")
  }
}

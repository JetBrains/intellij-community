// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.terminal.StyledTextConsumer
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer
import org.jetbrains.plugins.terminal.exp.TerminalUiUtils.toTextAttributes

class SimpleTerminalController(
  settings: JBTerminalSystemSettingsProviderBase,
  private val session: BlockTerminalSession,
  private val editor: EditorEx
) : Disposable {
  val document: Document
    get() = editor.document
  private val terminalModel: TerminalModel
    get() = session.model

  private val outputModel: TerminalOutputModel = TerminalOutputModel(editor)
  private val selectionModel = TerminalSelectionModel(outputModel)  // fake model, that won't be changed
  private val caretModel: TerminalCaretModel = TerminalCaretModel(session, outputModel, editor, parentDisposable = this)
  private val caretPainter: TerminalCaretPainter = TerminalCaretPainter(caretModel, outputModel, selectionModel, editor)

  var isFocused: Boolean = false

  init {
    editor.putUserData(TerminalDataContextUtils.IS_ALTERNATE_BUFFER_EDITOR_KEY, true)
    Disposer.register(this, caretPainter)

    // create dummy logical block, that will cover all the output, needed only for caret model
    outputModel.createBlock(command = null, directory = null)
    terminalModel.isCommandRunning = true

    setupContentListener()
    val eventsHandler = SimpleTerminalEventsHandler(session, settings, outputModel)
    setupKeyEventDispatcher(editor, settings, eventsHandler, outputModel, selectionModel, disposable = this)
    setupMouseListener(editor, settings, terminalModel, eventsHandler, disposable = this)
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
    return TerminalContent(builder.toString(), highlightings)
  }

  private fun updateEditor(content: TerminalContent) {
    document.setText(content.text)
    editor.highlighter = TerminalTextHighlighter(content.highlightings)

    val line = terminalModel.historyLinesCount + terminalModel.cursorY - 1
    editor.caretModel.moveToLogicalPosition(LogicalPosition(line, terminalModel.cursorX))
    editor.scrollingModel.scrollToCaret(ScrollType.CENTER_DOWN)
    caretPainter.repaint()
  }

  private fun TextStyle.toTextAttributes(): TextAttributes = this.toTextAttributes(session.colorPalette)

  override fun dispose() {}

  private data class TerminalContent(val text: String, val highlightings: List<HighlightingInfo>)

  companion object {
    val KEY: DataKey<SimpleTerminalController> = DataKey.create("SimpleTerminalController")
  }
}
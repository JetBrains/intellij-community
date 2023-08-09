// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.terminal.StyledTextConsumer
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer

class TerminalOutputController(private val editor: EditorEx,
                               private val session: TerminalSession,
                               private val settings: JBTerminalSystemSettingsProviderBase) {
  private val outputModel: TerminalOutputModel = TerminalOutputModel(editor)
  private val terminalModel: TerminalModel = session.model
  private val blocksDecorator: TerminalBlocksDecorator = TerminalBlocksDecorator(editor)

  var isFocused: Boolean = false

  private var runningListenersDisposable: Disposable? = null

  fun startCommandBlock(command: String?) {
    val block = outputModel.createBlock(command)
    val decoration = blocksDecorator.installDecoration(block)
    outputModel.putDecoration(block, decoration)

    val disposable = Disposer.newDisposable().also { Disposer.register(session, it) }
    runningListenersDisposable = disposable
    val eventsHandler = TerminalEventsHandler(session, settings)
    setupKeyEventDispatcher(editor, settings, eventsHandler, disposable, this::isFocused)
    setupMouseListener(editor, settings, session.model, eventsHandler, disposable)
    setupContentListener(disposable)
  }

  fun finishCommandBlock() {
    runningListenersDisposable?.let { Disposer.dispose(it) }
    invokeLater {
      val block = outputModel.getLastBlock() ?: error("No active block")
      val document = editor.document
      val lastLineInd = document.getLineNumber(block.endOffset)
      val lastLineStart = document.getLineStartOffset(lastLineInd)
      val lastLineText = document.getText(TextRange(lastLineStart, block.endOffset))
      // remove the line with empty prompt
      if (lastLineText.isBlank()) {
        // remove also the line break if it is not the first block
        val removeOffset = lastLineStart - if (lastLineStart > 0) 1 else 0
        document.deleteString(removeOffset, block.endOffset)
      }
      if (document.getText(block.textRange).isBlank()) {
        outputModel.removeBlock(block)
      }
    }
  }

  private fun setupContentListener(disposable: Disposable) {
    terminalModel.addContentListener(object : TerminalModel.ContentListener {
      override fun onContentChanged() {
        updateEditorContent()
      }
    }, disposable)
  }

  private fun updateEditorContent() {
    val content = computeTerminalContent()
    // Can not use invokeAndWait here because deadlock may happen. TerminalTextBuffer is locked at this place,
    // and EDT can be frozen now trying to acquire this lock
    invokeLater(ModalityState.any()) {
      if (!editor.isDisposed) {
        updateEditor(content)
      }
    }
  }

  private fun computeTerminalContent(): String {
    // todo: collect info about highlighting
    val builder = StringBuilder()
    val consumer = object : StyledTextConsumer {
      override fun consume(x: Int,
                           y: Int,
                           style: TextStyle,
                           characters: CharBuffer,
                           startRow: Int) {
        builder.append(characters.toString())
      }

      override fun consumeNul(x: Int,
                              y: Int,
                              nulIndex: Int,
                              style: TextStyle,
                              characters: CharBuffer,
                              startRow: Int) {
        repeat(characters.buf.size) {
          builder.append(' ')
        }
      }

      override fun consumeQueue(x: Int, y: Int, nulIndex: Int, startRow: Int) {
        builder.append("\n")
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
    }
    return builder.toString()
  }

  private fun updateEditor(content: String) {
    val block = outputModel.getLastBlock() ?: error("No active block")
    editor.document.replaceString(block.startOffset, block.endOffset, content)
    if (terminalModel.useAlternateBuffer) {
      editor.setCaretEnabled(false)
    }
    else {
      editor.setCaretEnabled(terminalModel.isCursorVisible)
      editor.caretModel.moveToOffset(block.endOffset)
      editor.scrollingModel.scrollToCaret(ScrollType.CENTER_DOWN)
    }
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.terminal.StyledTextConsumer
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.IS_OUTPUT_EDITOR_KEY
import org.jetbrains.plugins.terminal.exp.TerminalUiUtils.toTextAttributes
import java.awt.Font

class TerminalOutputController(
  private val editor: EditorEx,
  private val session: TerminalSession,
  private val settings: JBTerminalSystemSettingsProviderBase
) : TerminalModel.TerminalListener {
  val outputModel: TerminalOutputModel = TerminalOutputModel(editor)
  val selectionModel: TerminalSelectionModel = TerminalSelectionModel(outputModel)
  private val terminalModel: TerminalModel = session.model
  private val blocksDecorator: TerminalBlocksDecorator = TerminalBlocksDecorator(outputModel, editor)
  private val textHighlighter: TerminalTextHighlighter = TerminalTextHighlighter(outputModel)

  private val caretModel: TerminalCaretModel = TerminalCaretModel(session, outputModel, editor)
  private val caretPainter: TerminalCaretPainter = TerminalCaretPainter(caretModel, outputModel, selectionModel, editor)

  @Volatile
  private var keyEventsListenerDisposable: Disposable? = null

  @Volatile
  private var mouseAndContentListenersDisposable: Disposable? = null

  init {
    editor.putUserData(IS_OUTPUT_EDITOR_KEY, true)
    editor.highlighter = textHighlighter
    session.model.addTerminalListener(this)
    Disposer.register(session, caretModel)
  }

  @RequiresEdt
  fun startCommandBlock(command: String?, promptText: String?) {
    val block = outputModel.createBlock(command, promptText)
    if (block.withPrompt) {
      appendLineToBlock(block, promptText!!, createPromptHighlighting(block))
    }
    if (block.withCommand) {
      appendLineToBlock(block, command!!, createCommandHighlighting(block))
    }
    if (block.withPrompt || block.withCommand) {
      blocksDecorator.installDecoration(block, isFirstBlock = outputModel.getBlocksSize() == 1)
    }

    installRunningCommandListeners()
  }

  private fun installRunningCommandListeners() {
    val mouseAndContentDisposable = Disposer.newDisposable().also { Disposer.register(session, it) }
    mouseAndContentListenersDisposable = mouseAndContentDisposable
    val keyEventsDisposable = Disposer.newDisposable().also { Disposer.register(session, it) }
    keyEventsListenerDisposable = keyEventsDisposable

    val eventsHandler = BlockTerminalEventsHandler(session, settings, outputModel, selectionModel)
    setupKeyEventDispatcher(editor, settings, eventsHandler, outputModel, selectionModel, keyEventsDisposable)
    setupMouseListener(editor, settings, session.model, eventsHandler, mouseAndContentDisposable)
    setupContentListener(mouseAndContentDisposable)
  }

  private fun disposeRunningCommandListeners() {
    mouseAndContentListenersDisposable?.let { Disposer.dispose(it) }
    mouseAndContentListenersDisposable = null
    runInEdt {
      // Dispose at EDT, because there can be a race when focus listener trying to install TerminalEventDispatcher on EDT,
      // and this disposable is disposed on BGT. The dispatcher won't be removed as a result.
      keyEventsListenerDisposable?.let { Disposer.dispose(it) }
      keyEventsListenerDisposable = null
    }
  }

  fun finishCommandBlock(exitCode: Int) {
    disposeRunningCommandListeners()
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
        outputModel.getHighlightings(block)?.let { current ->
          val updated = current.filter { it.endOffset <= block.endOffset }
          outputModel.putHighlightings(block, updated)
        }
      }
      if (document.getText(block.textRange).isBlank()) {
        outputModel.removeBlock(block)
      }
      else if (exitCode != 0) {
        outputModel.addBlockState(block, ErrorBlockDecorationState())
      }
    }
  }

  @RequiresEdt
  fun insertEmptyLine() {
    outputModel.closeLastBlock()
    editor.document.insertString(editor.document.textLength, "\n")
    val visibleArea = editor.scrollingModel.visibleArea
    editor.scrollingModel.scrollVertically(editor.contentComponent.height - visibleArea.height)
  }

  override fun onAlternateBufferChanged(enabled: Boolean) {
    if (enabled) {
      // stop updating the block content, because alternate buffer application will be shown in a separate component
      disposeRunningCommandListeners()
    }
    else {
      installRunningCommandListeners()
      terminalModel.withContentLock {
        updateEditorContent()
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
    val output = computeCommandOutput()
    // Can not use invokeAndWait here because deadlock may happen. TerminalTextBuffer is locked at this place,
    // and EDT can be frozen now trying to acquire this lock
    invokeLater(ModalityState.any()) {
      if (!editor.isDisposed) {
        updateEditor(output)
      }
    }
  }

  private fun computeCommandOutput(): CommandOutput {
    val block = outputModel.getLastBlock()!!
    val baseOffset = block.outputStartOffset
    val builder = StringBuilder()
    val highlightings = mutableListOf<HighlightingInfo>()
    val consumer = object : StyledTextConsumer {
      override fun consume(x: Int,
                           y: Int,
                           style: TextStyle,
                           characters: CharBuffer,
                           startRow: Int) {
        val startOffset = baseOffset + builder.length
        builder.append(characters.toString())
        val attributes = style.toTextAttributes()
        highlightings.add(HighlightingInfo(startOffset, baseOffset + builder.length, attributes))
      }

      override fun consumeNul(x: Int,
                              y: Int,
                              nulIndex: Int,
                              style: TextStyle,
                              characters: CharBuffer,
                              startRow: Int) {
        val startOffset = baseOffset + builder.length
        repeat(characters.buf.size) {
          builder.append(' ')
        }
        highlightings.add(HighlightingInfo(startOffset, baseOffset + builder.length, TextStyle.EMPTY.toTextAttributes()))
      }

      override fun consumeQueue(x: Int, y: Int, nulIndex: Int, startRow: Int) {
        val startOffset = baseOffset + builder.length
        builder.append("\n")
        highlightings.add(HighlightingInfo(startOffset, startOffset + 1, TextStyle.EMPTY.toTextAttributes()))
      }
    }

    val commandLines = block.command?.let { command ->
      command.split("\n").sumOf { it.length / terminalModel.width + if (it.length % terminalModel.width > 0) 1 else 0 }
    } ?: 0
    val historyLines = terminalModel.historyLinesCount
    if (terminalModel.historyLinesCount > 0) {
      if (commandLines <= historyLines) {
        terminalModel.processHistoryAndScreenLines(commandLines - historyLines, historyLines - commandLines, consumer)
        terminalModel.processScreenLines(0, terminalModel.cursorY, consumer)
      }
      else {
        terminalModel.processHistoryAndScreenLines(-historyLines, historyLines, consumer)
        terminalModel.processScreenLines(commandLines - historyLines, terminalModel.cursorY, consumer)
      }
    }
    else {
      terminalModel.processScreenLines(commandLines, terminalModel.cursorY - commandLines, consumer)
    }

    while (builder.lastOrNull() == '\n') {
      builder.deleteCharAt(builder.lastIndex)
      highlightings.removeLast()
    }
    return CommandOutput(builder.toString(), highlightings)
  }

  private fun updateEditor(output: CommandOutput) {
    val block = outputModel.getLastBlock() ?: error("No active block")
    editor.document.replaceString(block.outputStartOffset, block.endOffset, output.text)

    // highlightings are collected only for output, so add prompt and command highlightings to the first place
    val highlightings = if (block.withPrompt || block.withCommand) {
      output.highlightings.toMutableList().also { highlightings ->
        if (block.withCommand) {
          highlightings.add(0, createCommandHighlighting(block))
        }
        if (block.withPrompt) {
          highlightings.add(0, createPromptHighlighting(block))
        }
      }
    }
    else output.highlightings

    outputModel.putHighlightings(block, highlightings)
    // Install decorations lazily, only if there is some text.
    // ZSH prints '%' character on startup and then removing it immediately, so ignore this character to avoid blinking.
    // This hack can be solved by debouncing the update text requests.
    if (outputModel.getDecoration(block) == null
        && output.text.isNotBlank()
        && output.text.trim() != "%") {
      blocksDecorator.installDecoration(block, isFirstBlock = outputModel.getBlocksSize() == 1)
    }

    editor.caretModel.moveToOffset(block.endOffset)
    editor.scrollingModel.scrollToCaret(ScrollType.CENTER_DOWN)
    // caret highlighter can be removed at this moment, because we replaced the text of the block
    // so, call repaint manually
    caretPainter.repaint()
  }

  private fun TextStyle.toTextAttributes(): TextAttributes = this.toTextAttributes(session.colorPalette)

  private fun appendLineToBlock(block: CommandBlock, text: String, highlighting: HighlightingInfo) {
    editor.document.insertString(block.endOffset, text + "\n")
    val existingHighlightings = outputModel.getHighlightings(block) ?: emptyList()
    outputModel.putHighlightings(block, existingHighlightings + highlighting)
  }

  /** It is implied that [CommandBlock.prompt] is not null */
  private fun createPromptHighlighting(block: CommandBlock): HighlightingInfo {
    val attributes = TextAttributes(TerminalUi.promptForeground, null, null, null, Font.PLAIN)
    return HighlightingInfo(block.startOffset, block.startOffset + block.prompt!!.length, attributes)
  }

  /** It is implied that [CommandBlock.command] is not null */
  private fun createCommandHighlighting(block: CommandBlock): HighlightingInfo {
    val attributes = TextAttributes(TerminalUi.commandForeground, null, null, null, Font.BOLD)
    return HighlightingInfo(block.commandStartOffset, block.commandStartOffset + block.command!!.length, attributes)
  }

  fun addDocumentListener(listener: DocumentListener, disposable: Disposable? = null) {
    if (disposable != null) {
      editor.document.addDocumentListener(listener, disposable)
    }
    else editor.document.addDocumentListener(listener)
  }

  private data class CommandOutput(val text: String, val highlightings: List<HighlightingInfo>)

  companion object {
    val KEY: DataKey<TerminalOutputController> = DataKey.create("TerminalOutputController")
  }
}
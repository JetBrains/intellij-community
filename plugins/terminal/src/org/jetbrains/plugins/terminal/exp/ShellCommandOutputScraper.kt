// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.StyledTextConsumer
import com.jediterm.terminal.Terminal
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.TerminalModelListener
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.plugins.terminal.TerminalUtil
import java.util.concurrent.CopyOnWriteArrayList

internal class ShellCommandOutputScraper(private val textBuffer: TerminalTextBuffer,
                                         private val terminal: Terminal,
                                         commandManager: ShellCommandManager,
                                         parentDisposable: Disposable) {

  constructor(session: TerminalSession): this(session.model.textBuffer, session.controller, session.commandManager, session)

  private val listeners: CopyOnWriteArrayList<ShellCommandOutputListener> = CopyOnWriteArrayList()
  private var runningCommand: String? = null

  init {
    val terminalListener = TerminalModelListener { onContentChanged() }
    textBuffer.addModelListener(terminalListener)
    Disposer.register(parentDisposable) {
      textBuffer.removeModelListener(terminalListener)
    }
    commandManager.addListener(object: ShellCommandListener {
      override fun commandStarted(command: String) {
        runningCommand = command
      }
    }, parentDisposable)
  }

  fun addListener(listener: ShellCommandOutputListener, parentDisposable: Disposable) {
    TerminalUtil.addItem(listeners, listener, parentDisposable)
  }

  private fun onContentChanged() {
    val output = computeCommandOutput()
    for (listener in listeners) {
      listener.commandOutputChanged(output)
    }
  }

  private fun computeCommandOutput(): StyledCommandOutput {
    val baseOffset = 0
    val builder = StringBuilder()
    val highlightings = mutableListOf<StyleRange>()
    val consumer = object : StyledTextConsumer {
      override fun consume(x: Int,
                           y: Int,
                           style: TextStyle,
                           characters: CharBuffer,
                           startRow: Int) {
        val startOffset = baseOffset + builder.length
        builder.append(characters.toString())
        highlightings.add(StyleRange(startOffset, baseOffset + builder.length, style))
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
        highlightings.add(StyleRange(startOffset, baseOffset + builder.length, TextStyle.EMPTY))
      }

      override fun consumeQueue(x: Int, y: Int, nulIndex: Int, startRow: Int) {
        val startOffset = baseOffset + builder.length
        builder.append("\n")
        highlightings.add(StyleRange(startOffset, startOffset + 1, TextStyle.EMPTY))
      }
    }

    val commandLines = runningCommand?.let { command ->
      command.split("\n").sumOf { it.length / textBuffer.width + if (it.length % textBuffer.width > 0) 1 else 0 }
    } ?: 0
    val historyLines = textBuffer.historyLinesCount
    if (historyLines > 0) {
      if (commandLines <= historyLines) {
        textBuffer.processHistoryAndScreenLines(commandLines - historyLines, historyLines - commandLines, consumer)
        textBuffer.processScreenLines(0, terminal.cursorY, consumer)
      }
      else {
        textBuffer.processHistoryAndScreenLines(-historyLines, historyLines, consumer)
        textBuffer.processScreenLines(commandLines - historyLines, terminal.cursorY, consumer)
      }
    }
    else {
      textBuffer.processScreenLines(commandLines, terminal.cursorY - commandLines, consumer)
    }

    while (builder.lastOrNull() == '\n') {
      builder.deleteCharAt(builder.lastIndex)
      highlightings.removeLast()
    }
    return StyledCommandOutput(builder.toString(), highlightings)
  }
}

data class StyledCommandOutput(val text: String, val styleRanges: List<StyleRange>)
data class StyleRange(val startOffset: Int, val endOffset: Int, val style: TextStyle)

internal interface ShellCommandOutputListener {
  fun commandOutputChanged(output: StyledCommandOutput) {}
}
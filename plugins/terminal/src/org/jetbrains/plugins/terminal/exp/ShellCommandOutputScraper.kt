// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.util.Alarm
import com.jediterm.terminal.StyledTextConsumer
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.plugins.terminal.TerminalUtil
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal class ShellCommandOutputScraper(private val session: TerminalSession,
                                         private val textBuffer: TerminalTextBuffer,
                                         commandManager: ShellCommandManager,
                                         parentDisposable: Disposable) {

  constructor(session: TerminalSession): this(session, session.model.textBuffer, session.commandManager, session)

  private val listeners: CopyOnWriteArrayList<ShellCommandOutputListener> = CopyOnWriteArrayList()
  private var runningCommand: String? = null
  private val contentChangedAlarm: Alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable)
  private val scheduled: AtomicBoolean = AtomicBoolean(false)

  init {
    TerminalUtil.addModelListener(textBuffer, parentDisposable) {
      onContentChanged()
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
    if (listeners.isNotEmpty()) {
      if (scheduled.compareAndSet(false, true)) {
        val request = {
          scheduled.set(false)
          if (listeners.isNotEmpty()) {
            val output = scrapeOutput()
            for (listener in listeners) {
              listener.commandOutputChanged(output)
            }
          }
        }
        contentChangedAlarm.addRequest(request, 50)
      }
    }
  }

  fun scrapeOutput(): StyledCommandOutput {
    session.model.withContentLock {
      return computeCommandOutput()
    }
  }

  private fun computeCommandOutput(): StyledCommandOutput {
    val baseOffset = 0
    val builder = StringBuilder()
    val highlightings = mutableListOf<StyleRange>()
    val consumer = object : StyledTextConsumer {

      private var pendingNewLines: Int = 0
      private var pendingNuls: Int = 0

      override fun consume(x: Int,
                           y: Int,
                           style: TextStyle,
                           characters: CharBuffer,
                           startRow: Int) {
        val text = characters.toString()
        if (text.isNotEmpty()) {
          repeat(pendingNewLines) {
            val startOffset = baseOffset + builder.length
            builder.append("\n")
            highlightings.add(StyleRange(startOffset, startOffset + 1, TextStyle.EMPTY))
          }
          pendingNewLines = 0
          repeat(pendingNuls) {
            builder.append(' ')
          }
          pendingNuls = 0
          val startOffset = baseOffset + builder.length
          builder.append(text)
          highlightings.add(StyleRange(startOffset, baseOffset + builder.length, style))
        }
      }

      override fun consumeNul(x: Int,
                              y: Int,
                              nulIndex: Int,
                              style: TextStyle,
                              characters: CharBuffer,
                              startRow: Int) {
        pendingNuls += characters.length
      }

      override fun consumeQueue(x: Int, y: Int, nulIndex: Int, startRow: Int) {
        pendingNewLines++
        pendingNuls = 0
      }
    }
    val historyLinesCount = textBuffer.historyLinesCount
    textBuffer.processHistoryAndScreenLines(-historyLinesCount, historyLinesCount + textBuffer.height, consumer)
    return StyledCommandOutput(builder.toString(), highlightings)
  }
}

data class StyledCommandOutput(val text: String, val styleRanges: List<StyleRange>)
data class StyleRange(val startOffset: Int, val endOffset: Int, val style: TextStyle)

internal interface ShellCommandOutputListener {
  fun commandOutputChanged(output: StyledCommandOutput) {}
}
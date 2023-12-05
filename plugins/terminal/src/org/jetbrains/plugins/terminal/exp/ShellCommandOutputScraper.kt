// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.util.Alarm
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.LinesBuffer
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.util.CharUtils
import org.jetbrains.plugins.terminal.TerminalUtil
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal class ShellCommandOutputScraper(private val session: TerminalSession,
                                         private val textBuffer: TerminalTextBuffer,
                                         parentDisposable: Disposable) {

  constructor(session: TerminalSession): this(session, session.model.textBuffer, session)

  private val listeners: MutableList<ShellCommandOutputListener> = CopyOnWriteArrayList()
  private val contentChangedAlarm: Alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable)
  private val scheduled: AtomicBoolean = AtomicBoolean(false)

  init {
    TerminalUtil.addModelListener(textBuffer, parentDisposable) {
      onContentChanged()
    }
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
      val outputBuilder = OutputBuilder()
      if (!textBuffer.isUsingAlternateBuffer) {
        outputBuilder.addLines(textBuffer.historyBuffer)
      }
      outputBuilder.addLines(textBuffer.screenBuffer)
      return outputBuilder.build()
    }
  }
}

private class OutputBuilder {
  private val output: StringBuilder = StringBuilder()
  private val styles: MutableList<StyleRange> = mutableListOf()
  private var pendingNewLines: Int = 0

  fun addLines(linesBuffer: LinesBuffer) {
    for (i in 0 until linesBuffer.lineCount) {
      addLine(linesBuffer.getLine(i))
    }
  }

  private fun addLine(line: TerminalLine) {
    line.forEachEntry { entry ->
      if (entry.text.isNotEmpty() && !entry.isNul) {
        addTextChunk(entry.text.normalize(), entry.style)
      }
    }
    if (!line.isWrapped) {
      pendingNewLines++
    }
  }

  private fun CharBuffer.normalize(): String {
    val s = this.toString()
    return if (s.contains(CharUtils.DWC)) s.filterTo(StringBuilder(s.length - 1)) { it != CharUtils.DWC }.toString() else s
  }

  private fun addTextChunk(text: String, style: TextStyle) {
    if (text.isNotEmpty()) {
      repeat(pendingNewLines) {
        output.append("\n")
      }
      pendingNewLines = 0
      val startOffset = output.length
      output.append(text)
      if (style != TextStyle.EMPTY) {
        styles.add(StyleRange(startOffset, startOffset + text.length, style))
      }
    }
  }

  fun build(): StyledCommandOutput {
    return StyledCommandOutput(output.toString(), styles)
  }
}

data class StyledCommandOutput(val text: String, val styleRanges: List<StyleRange>)
data class StyleRange(val startOffset: Int, val endOffset: Int, val style: TextStyle)

internal interface ShellCommandOutputListener {
  fun commandOutputChanged(output: StyledCommandOutput) {}
}
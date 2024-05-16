// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.util.Alarm
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.LinesBuffer
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.plugins.terminal.TerminalUtil
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal class ShellCommandOutputScraper(
  private val session: BlockTerminalSession,
  textBuffer: TerminalTextBuffer,
  parentDisposable: Disposable
) {

  constructor(session: BlockTerminalSession) : this(session, session.model.textBuffer, session)

  private val listeners: MutableList<ShellCommandOutputListener> = CopyOnWriteArrayList()
  private val contentChangedAlarm: Alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable)
  private val scheduled: AtomicBoolean = AtomicBoolean(false)

  @Volatile
  private var useExtendedDelayOnce: Boolean = false

  init {
    TerminalUtil.addModelListener(textBuffer, parentDisposable) {
      onContentChanged()
    }
  }

  /**
   * @param useExtendedDelayOnce whether to send first content update with greater delay than default
   */
  fun addListener(listener: ShellCommandOutputListener, parentDisposable: Disposable, useExtendedDelayOnce: Boolean = false) {
    TerminalUtil.addItem(listeners, listener, parentDisposable)
    this.useExtendedDelayOnce = useExtendedDelayOnce
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
        val delay = if (useExtendedDelayOnce) 150 else 50
        useExtendedDelayOnce = false
        contentChangedAlarm.addRequest(request, delay)
      }
    }
  }

  fun scrapeOutput(): StyledCommandOutput = session.model.withContentLock { scrapeOutput(session) }

  companion object {
    fun scrapeOutput(session: BlockTerminalSession): StyledCommandOutput {
      return scrapeOutput(session.model.textBuffer, session.commandBlockIntegration.commandEndMarker)
    }

    fun scrapeOutput(textBuffer: TerminalTextBuffer, commandEndMarker: String? = null): StyledCommandOutput {
      val outputBuilder = OutputBuilder(commandEndMarker)
      if (!textBuffer.isUsingAlternateBuffer) {
        outputBuilder.addLines(textBuffer.historyBuffer)
      }
      outputBuilder.addLines(textBuffer.screenBuffer)
      return outputBuilder.build()
    }
  }
}

private class OutputBuilder(private val commandEndMarker: String?) {
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
    val text = output.toString()
    if (commandEndMarker != null) {
      val trimmedText = text.trimEnd()
      val commandEndMarkerFound = trimmedText.endsWith(commandEndMarker)
      if (commandEndMarkerFound) {
        val outputText = trimmedText.dropLast(commandEndMarker.length)
        return StyledCommandOutput(outputText, true, styles)
      }
      else {
        // investigate why ConPTY inserts hard line breaks sometimes
        val suffixStartInd = findSuffixStartIndIgnoringLF(trimmedText, commandEndMarker)
        if (suffixStartInd >= 0) {
          val commandText = trimmedText.substring(0, suffixStartInd)
          return StyledCommandOutput(commandText, true, styles)
        }
      }
    }
    return StyledCommandOutput(text, false, styles)
  }

  /**
   * @return the index in [text] where [suffix] starts, or -1 if there is no such suffix
   */
  private fun findSuffixStartIndIgnoringLF(text: String, suffix: String): Int {
    check(suffix.isNotEmpty())
    if (text.length < suffix.length) return -1
    var textInd: Int = text.length
    for (suffixInd in suffix.length - 1 downTo 0) {
      textInd--
      while (textInd >= 0 && text[textInd] == '\n') {
        textInd--
      }
      if (textInd < 0 || text[textInd] != suffix[suffixInd]) {
        return -1
      }
    }
    return textInd
  }
}

internal data class StyledCommandOutput(val text: String, val commandEndMarkerFound: Boolean, val styleRanges: List<StyleRange>)
internal data class StyleRange(val startOffset: Int, val endOffset: Int, val style: TextStyle)

internal interface ShellCommandOutputListener {
  fun commandOutputChanged(output: StyledCommandOutput) {}
}

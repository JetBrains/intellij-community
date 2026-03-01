// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session

import com.intellij.openapi.Disposable
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.block.session.scraper.DropTrailingNewLinesStringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.SimpleStringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.StringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.StylesCollectingTerminalLinesCollector
import org.jetbrains.plugins.terminal.block.session.scraper.TerminalLinesCollector
import org.jetbrains.plugins.terminal.block.session.util.Debouncer
import org.jetbrains.plugins.terminal.block.ui.withLock
import org.jetbrains.plugins.terminal.session.impl.StyleRange
import org.jetbrains.plugins.terminal.util.addModelListener
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max

@ApiStatus.Internal
class ShellCommandOutputScraperImpl(
  private val textBuffer: TerminalTextBuffer,
  parentDisposable: Disposable,
  debounceTimeout: Int = 50,
) : ShellCommandOutputScraper {

  constructor(session: BlockTerminalSession) : this(
    session.model.textBuffer,
    session as Disposable
  )

  private val listeners: MutableList<ShellCommandOutputListener> = CopyOnWriteArrayList()

  private val debouncer: Debouncer = Debouncer(debounceTimeout, parentDisposable)

  init {
    textBuffer.addModelListener(parentDisposable) {
      onContentChanged()
    }
  }

  /**
   * @param useExtendedDelayOnce whether to send first content update with greater delay than default
   */
  override fun addListener(listener: ShellCommandOutputListener, parentDisposable: Disposable, useExtendedDelayOnce: Boolean) {
    TerminalUtil.addItem(listeners, listener, parentDisposable)
    this.debouncer.setExtendedDelayOnce()
  }

  private fun onContentChanged() {
    if (listeners.isNotEmpty()) {
      debouncer.execute(::doOnContentChanged)
    }
  }

  private fun doOnContentChanged() {
    if (listeners.isNotEmpty()) {
      val output = scrapeOutput()
      for (listener in listeners) {
        listener.commandOutputChanged(output)
      }
    }
  }

  override fun scrapeOutput(): StyledCommandOutput = textBuffer.withLock {
    scrapeOutput(textBuffer)
  }

  companion object {
    fun scrapeOutput(session: BlockTerminalSession): StyledCommandOutput {
      return scrapeOutput(session.model.textBuffer)
    }

    /**
     * @param startLine index of the line in the [TerminalTextBuffer] coordinate system.
     * Positive indexes for the screen lines, negative - for the history.
     * So, the 0th line is the first screen line, -1 line is the last history line.
     */
    fun scrapeOutput(
      textBuffer: TerminalTextBuffer,
      startLine: Int = -textBuffer.historyLinesCount,
    ): StyledCommandOutput {
      val stringCollector: StringCollector = DropTrailingNewLinesStringCollector(SimpleStringCollector())

      val styles: MutableList<StyleRange> = mutableListOf()
      val styleCollectingOutputBuilder: TerminalLinesCollector = StylesCollectingTerminalLinesCollector(stringCollector, styles::add)
      val terminalLinesCollector: TerminalLinesCollector = styleCollectingOutputBuilder
      textBuffer.collectLines(terminalLinesCollector, startLine)
      return StyledCommandOutput(stringCollector.buildText(), styles)
    }
  }
}

@ApiStatus.Internal
data class StyledCommandOutput(val text: String, val styleRanges: List<StyleRange>)

@ApiStatus.Internal
interface ShellCommandOutputListener {
  fun commandOutputChanged(output: StyledCommandOutput) {}
}

internal const val NEW_LINE: Char = '\n'
internal const val NEW_LINE_STRING: String = NEW_LINE.toString()

/**
 * @param startLine index of the line in the [TerminalTextBuffer] coordinate system.
 * Positive indexes for the screen lines, negative - for the history.
 * So, the 0th line is the first screen line, -1 line is the last history line.
 */
@ApiStatus.Internal
fun TerminalTextBuffer.collectLines(
  terminalLinesCollector: TerminalLinesCollector,
  startLine: Int = -historyLinesCount,
) {
  val adjustedStartLine = if (isUsingAlternateBuffer) max(0, startLine) else startLine
  for (ind in adjustedStartLine until screenLinesCount.coerceAtMost(height)) {
    terminalLinesCollector.addLine(getLine(ind))
  }
  terminalLinesCollector.flush()
}

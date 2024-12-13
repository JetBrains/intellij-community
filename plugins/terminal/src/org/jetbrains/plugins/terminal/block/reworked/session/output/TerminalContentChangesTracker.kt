// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.output

import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.model.TextBufferChangesListener
import org.jetbrains.plugins.terminal.block.session.StyleRange
import org.jetbrains.plugins.terminal.block.session.StyledCommandOutput
import org.jetbrains.plugins.terminal.block.session.collectLines
import org.jetbrains.plugins.terminal.block.session.scraper.SimpleStringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.StylesCollectingTerminalLinesCollector
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

internal class TerminalContentChangesTracker(
  private val textBuffer: TerminalTextBuffer,
  private val discardedHistoryTracker: TerminalDiscardedHistoryTracker,
) {
  private var lastChangedVisualLine: Int = 0
  private var anyLineChanged: Boolean = false

  private val listeners: MutableList<(TerminalContentUpdatedEvent) -> Unit> = CopyOnWriteArrayList()

  init {
    textBuffer.addChangesListener(object : TextBufferChangesListener {
      override fun linesChanged(fromIndex: Int) {
        val line = textBuffer.effectiveHistoryLinesCount + fromIndex
        lastChangedVisualLine = min(lastChangedVisualLine, line)
        anyLineChanged = true
      }

      override fun linesDiscardedFromHistory(lines: List<TerminalLine>) {
        if (textBuffer.isUsingAlternateBuffer) {
          return
        }

        if (lastChangedVisualLine >= lines.size) {
          lastChangedVisualLine -= lines.size
        }
        else {
          val additionalLines = lines.subList(lastChangedVisualLine, lines.size)
          lastChangedVisualLine = 0
          flushChanges(additionalLines)
        }
      }

      override fun widthResized() {
        // Consider resize of the width as a full replacement of the output.
        // Because lines layout might be changed and our stored last changed visual line index might become incorrect.
        // As a solution, we may track the last changed logical line - its index should not be affected by resizing.
        // But there is a problem with resize: some lines might be discarded from the Text Buffer during this operation,
        // and it is not tracked now.
        // TODO: consider tracking of the discarded lines in case of resizing to not replace everything.
        lastChangedVisualLine = 0
      }
    })
  }

  fun addHistoryOverflowListener(listener: (TerminalContentUpdatedEvent) -> Unit) {
    listeners.add(listener)
  }

  fun getContentUpdate(): TerminalContentUpdatedEvent? {
    return getContentUpdate(emptyList())
  }

  private fun flushChanges(additionalLines: List<TerminalLine>) {
    val update = getContentUpdate(additionalLines)!!
    listeners.forEach {
      it(update)
    }
  }

  private fun getContentUpdate(additionalLines: List<TerminalLine>): TerminalContentUpdatedEvent? {
    return if (anyLineChanged) {
      collectOutput(additionalLines)
    }
    else null
  }

  private fun collectOutput(additionalLines: List<TerminalLine>): TerminalContentUpdatedEvent {
    check(anyLineChanged) { "It is expected that this method is called only if something is changed" }

    // Transform to the TextBuffer coordinates: negative indexes for history, positive for the screen.
    var startLine = lastChangedVisualLine - textBuffer.effectiveHistoryLinesCount

    // Ensure that startLine is either not a wrapped line or the start of the wrapped line.
    while (startLine - 1 >= -textBuffer.effectiveHistoryLinesCount && textBuffer.getLine(startLine - 1).isWrapped) {
      startLine--
    }

    val output: StyledCommandOutput = scrapeOutput(startLine, additionalLines)
    // It is the absolut logical line index from the start of the output tracking (including lines already dropped from the history)
    val logicalLineIndex = textBuffer.getLogicalLineIndex(startLine) + discardedHistoryTracker.getDiscardedLogicalLinesCount() - additionalLines.size

    lastChangedVisualLine = textBuffer.effectiveHistoryLinesCount + textBuffer.screenLinesCount
    anyLineChanged = false

    return TerminalContentUpdatedEvent(output.text, output.styleRanges, logicalLineIndex)
  }

  private fun scrapeOutput(startLine: Int, additionalLines: List<TerminalLine>): StyledCommandOutput {
    val styles = mutableListOf<StyleRange>()
    val stringCollector = SimpleStringCollector()
    val terminalLinesCollector = StylesCollectingTerminalLinesCollector(stringCollector, styles::add)

    for (line in additionalLines) {
      terminalLinesCollector.addLine(line)
    }
    textBuffer.collectLines(terminalLinesCollector, startLine)

    return StyledCommandOutput(stringCollector.buildText(), false, styles)
  }
}

/**
 * Consider the sequence of wrapped lines in the Text Buffer as a single logical line.
 */
internal fun TerminalTextBuffer.getLogicalLineIndex(visualLine: Int): Int {
  var count = 0
  for (ind in -effectiveHistoryLinesCount until visualLine) {
    if (!getLine(ind).isWrapped) {
      count++
    }
  }
  return count
}

private val TerminalTextBuffer.effectiveHistoryLinesCount: Int
  get() = if (isUsingAlternateBuffer) 0 else historyLinesCount
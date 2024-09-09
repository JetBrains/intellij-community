// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.model.TextBufferChangesListener
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.plugins.terminal.block.session.StyleRange
import org.jetbrains.plugins.terminal.block.session.TerminalModel.Companion.withLock
import org.jetbrains.plugins.terminal.block.session.scraper.DropTrailingNewLinesStringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.SimpleStringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.StylesCollectingTerminalLinesCollector
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

/**
 * Tracks the changes in the [TerminalTextBuffer] and allows collecting them
 * by using [collectChangedOutputOrWait] or [collectChangedOutputOrNull].
 * Sequential calls to collect the output will scrap only those lines changed between the calls.
 *
 * The tracking is started right in the constructor and finished when parentDisposable is disposed.
 */
internal class TerminalOutputChangesTracker(
  private val textBuffer: TerminalTextBuffer,
  parentDisposable: Disposable,
) {
  /**
   * Index of the last changed line in the Text Buffer.
   * Zero-based, so the 0th line is the last line in the history.
   * Guarded by the TerminalTextBuffer lock.
   */
  private var lastChangedVisualLine: Int = 0

  /**
   * Count of logical lines (a sequence of wrapped lines in the Text Buffer is counted as a single logical line)
   * dropped from the history because of exceeding the history size limit.
   * Guarded by the TerminalTextBuffer lock.
   */
  private var discardedLogicalLinesCount: Int = 0

  /**
   * True initially, because we consider all Text Buffer content as changed at the moment of initialization.
   * Guarded by the TerminalTextBuffer lock.
   */
  private var wasAnyLineChanged: Boolean = true

  /**
   * Whether some lines were discarded from the history before being collected.
   * Guarded by the TerminalTextBuffer lock.
   */
  private var wereChangesDiscarded: Boolean = false

  private val changeListeners: MutableList<() -> Unit> = CopyOnWriteArrayList()

  init {
    val listener = object : TextBufferChangesListener {
      override fun linesChanged(fromIndex: Int) {
        val line = textBuffer.historyLinesCount + fromIndex
        lastChangedVisualLine = min(lastChangedVisualLine, line)
        wasAnyLineChanged = true

        for (listener in changeListeners) {
          listener()
        }
      }

      override fun linesDiscardedFromHistory(lines: List<TerminalLine>) {
        if (lastChangedVisualLine >= lines.size) {
          lastChangedVisualLine -= lines.size
        }
        else {
          lastChangedVisualLine = 0
          wereChangesDiscarded = true
        }

        for (line in lines) {
          if (!line.isWrapped) {
            discardedLogicalLinesCount++
          }
        }
      }

      override fun widthResized() {
        // Consider resize of the width as a full replacement of the output.
        // Because in the process of this operation, some lines might be discarded from the Text Buffer,
        // and it is not tracked now, so it may bring the inconsistency if we omit it.
        // Todo: consider tracking of the discarded lines in case of resizing to not replace everything.
        lastChangedVisualLine = 0
        wasAnyLineChanged = true
        wereChangesDiscarded = true
      }
    }

    textBuffer.addChangesListener(listener)
    Disposer.register(parentDisposable) {
      textBuffer.removeChangesListener(listener)
    }
  }

  /**
   * Collects the changed output from the moment of the last collection.
   * If there is no changed output, it will suspend until something is changed.
   */
  suspend fun collectChangedOutputOrWait(): PartialCommandOutput {
    val deferred = textBuffer.withLock {
      if (wasAnyLineChanged) {
        CompletableDeferred(collectOutput())
      }
      else getChangedOutputDeferred()
    }
    return deferred.await()
  }

  /**
   * Collects the changed output from the moment of the last collection.
   * If there is no changed output, then just returns null.
   */
  fun collectChangedOutputOrNull(): PartialCommandOutput? {
    return textBuffer.withLock {
      if (wasAnyLineChanged) {
        collectOutput()
      }
      else null
    }
  }

  private fun getChangedOutputDeferred(): CompletableDeferred<PartialCommandOutput> {
    assert(!wasAnyLineChanged) { "Something was changed already, no need to wait for next change" }

    val deferred = CompletableDeferred<PartialCommandOutput>()
    val listener: () -> Unit = {
      assert(wasAnyLineChanged) { "Nothing was changed, but change event fired" }
      deferred.complete(collectOutput())
    }
    deferred.invokeOnCompletion {
      changeListeners.remove(listener)
    }
    changeListeners.add(listener)

    return deferred
  }

  private fun collectOutput(): PartialCommandOutput {
    // Transform to the TextBuffer coordinates: negative indexes for history, positive for the screen.
    var startLine = lastChangedVisualLine - textBuffer.historyLinesCount

    // Ensure that startLine is either not a wrapped line or the start of the wrapped line.
    while (startLine - 1 >= -textBuffer.historyLinesCount && textBuffer.getLine(startLine - 1).isWrapped) {
      startLine--
    }

    val (text, styles) = collectChangedLines(startLine)
    // It is the absolut logical line index from the start of the output tracking (including lines already dropped from the history)
    val logicalLineIndex = getLogicalLineIndex(startLine) + discardedLogicalLinesCount
    val anyDiscarded = wereChangesDiscarded

    lastChangedVisualLine = textBuffer.historyLinesCount
    wasAnyLineChanged = false
    wereChangesDiscarded = false

    return PartialCommandOutput(text, styles, logicalLineIndex, textBuffer.width, anyDiscarded)
  }

  private fun collectChangedLines(startLine: Int): Pair<String, List<StyleRange>> {
    val stringCollector = DropTrailingNewLinesStringCollector(SimpleStringCollector())
    val styles: MutableList<StyleRange> = mutableListOf()
    val styleCollectingOutputBuilder = StylesCollectingTerminalLinesCollector(stringCollector, styles::add)
    for (index in startLine until textBuffer.screenLinesCount) {
      val line = textBuffer.getLine(index)
      styleCollectingOutputBuilder.addLine(line)
    }

    val text = stringCollector.buildText()
    return text to styles
  }

  /**
   * Consider the sequence of wrapped lines in the Text Buffer as a single logical line.
   */
  private fun getLogicalLineIndex(visualLine: Int): Int {
    var count = 0
    for (ind in -textBuffer.historyLinesCount until visualLine) {
      if (!textBuffer.getLine(ind).isWrapped) {
        count++
      }
    }
    return count
  }
}

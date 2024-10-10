// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.model.TextBufferChangesListener
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.plugins.terminal.block.session.ShellCommandOutputScraperImpl
import org.jetbrains.plugins.terminal.block.session.StyledCommandOutput
import org.jetbrains.plugins.terminal.block.session.TerminalModel.Companion.withLock
import org.jetbrains.plugins.terminal.util.ShellIntegration
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
  private val shellIntegration: ShellIntegration,
  parentDisposable: Disposable,
  onUpdateStart: () -> Unit,
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
  private var isAnyLineChanged: Boolean = true

  /**
   * Whether some lines were discarded from the history before being collected.
   *
   * For example, imagine that the history capacity of the Text Buffer is 5000 lines.
   * And we have 1000 changed lines, we collect them and now [lastChangedVisualLine] is 1000.
   * Then 6000 lines are added to the buffer.
   * We have to drop 2000 lines from the history because of 5000 lines limit.
   * And now [lastChangedVisualLine] is 0, and we dropped 1000 not yet collected lines.
   * In this case this property will be true to indicate that for the next output collection.
   *
   * Guarded by the TerminalTextBuffer lock.
   */
  private var isChangesDiscarded: Boolean = false

  private val changeListeners: MutableList<() -> Unit> = CopyOnWriteArrayList()

  init {
    val listener = object : TextBufferChangesListener {
      override fun linesChanged(fromIndex: Int) = textBuffer.withLock {
        onUpdateStart()

        val line = textBuffer.historyLinesCount + fromIndex
        lastChangedVisualLine = min(lastChangedVisualLine, line)
        isAnyLineChanged = true

        for (listener in changeListeners) {
          listener()
        }
      }

      override fun linesDiscardedFromHistory(lines: List<TerminalLine>) = textBuffer.withLock {
        if (lastChangedVisualLine >= lines.size) {
          lastChangedVisualLine -= lines.size
        }
        else {
          lastChangedVisualLine = 0
          isChangesDiscarded = true
        }

        for (line in lines) {
          if (!line.isWrapped) {
            discardedLogicalLinesCount++
          }
        }
      }

      override fun widthResized() = textBuffer.withLock {
        // Consider resize of the width as a full replacement of the output.
        // Because in the process of this operation, some lines might be discarded from the Text Buffer,
        // and it is not tracked now, so it may bring the inconsistency if we omit it.
        // Todo: consider tracking of the discarded lines in case of resizing to not replace everything.
        lastChangedVisualLine = 0
        isAnyLineChanged = true
        isChangesDiscarded = true
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
      if (isAnyLineChanged) {
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
      if (isAnyLineChanged) {
        collectOutput()
      }
      else null
    }
  }

  private fun getChangedOutputDeferred(): CompletableDeferred<PartialCommandOutput> {
    check(!isAnyLineChanged) { "Something was changed already, no need to wait for next change" }

    val deferred = CompletableDeferred<PartialCommandOutput>()
    val listener: () -> Unit = {
      check(isAnyLineChanged) { "Nothing was changed, but change event fired" }
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

    val output: StyledCommandOutput = ShellCommandOutputScraperImpl.scrapeOutput(
      textBuffer,
      shellIntegration.commandBlockIntegration?.commandEndMarker,
      startLine
    )
    // It is the absolut logical line index from the start of the output tracking (including lines already dropped from the history)
    val logicalLineIndex = getLogicalLineIndex(startLine) + discardedLogicalLinesCount
    val anyDiscarded = isChangesDiscarded

    lastChangedVisualLine = textBuffer.historyLinesCount
    isAnyLineChanged = false
    isChangesDiscarded = false

    return PartialCommandOutput(output.text, output.styleRanges, logicalLineIndex, textBuffer.width, anyDiscarded)
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

package com.intellij.terminal.frontend.view.hyperlinks

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener

@ApiStatus.Internal
class TerminalOutputModelChangesTracker(
  private val outputModel: TerminalOutputModel,
  parentDisposable: Disposable,
) {
  // Variables should be accessed only from EDT
  private var contentChanged: Boolean = true
  private var firstChangedLine: TerminalLineIndex = outputModel.firstLineIndex

  /**
   * Monotonic history of changes: both [ChangeInfo.modificationStamp] and [ChangeInfo.startOffset]
   * strictly increase along the deque (see [recordChange]).
   * This is the classic sliding-window-minimum: the minimum changed
   * offset among all changes newer than a given stamp is simply the first such entry.
   */
  private val changesHistory = ArrayDeque<ChangeInfo>(initialCapacity = MAX_CHANGES_HISTORY_LENGTH)

  /**
   * The [ChangeInfo.modificationStamp] of the newest change that was evicted from [changesHistory] because it overflowed.
   * Starts at [Long.MIN_VALUE] so nothing is considered lost until the first eviction.
   */
  private var lastEvictedStamp: Long = Long.MIN_VALUE

  init {
    outputModel.addListener(parentDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        if (!event.isTrimming) {
          val line = event.model.getLineByOffset(event.offset)
          firstChangedLine = minOf(firstChangedLine, line)
        }
        contentChanged = true
      }
    })
  }

  /**
   * Returns the first changed line index since the last call.
   */
  @RequiresEdt
  fun getFirstChangedLineAndReset(): TerminalLineIndex? {
    if (!contentChanged) return null

    // The stored line may be below `outputModel.firstLineIndex` if trim happened after it was recorded.
    // Clamp it, so callers never see a line that no longer exists in the model.
    val line = maxOf(firstChangedLine, outputModel.firstLineIndex)
    recordChange(line)

    contentChanged = false
    firstChangedLine = outputModel.lastLineIndex
    return line
  }

  /**
   * Analyzes the output model changes history to find the range of content that was changed since the [modificationStamp].
   * Returns the start of this range - the first changed character offset.
   *
   * Returns `null` when the answer can't be correctly calculated: a change newer than [modificationStamp] has already been evicted
   * from the bounded history (too many changes happened while the result was being computed).
   */
  @RequiresEdt
  fun getFirstChangedOffsetSinceStamp(modificationStamp: Long): TerminalOffset? {
    // Record a change that already happened but hasn't been saved into the history,
    // so a hyperlinks result processed between flushes sees the up-to-date changed region.
    if (contentChanged) {
      recordChange(maxOf(firstChangedLine, outputModel.firstLineIndex))
    }

    // A change newer than the stamp was evicted: we can't know the real first-changed offset anymore.
    if (modificationStamp < lastEvictedStamp) {
      return null
    }

    val searchResult = changesHistory.binarySearch { changeInfo ->
      if (changeInfo.modificationStamp <= modificationStamp) -1 else 1
    }
    val nextChangeIndex = -searchResult - 1
    if (nextChangeIndex == changesHistory.size) {
      // No changes after the specified stamp, return the end of the model
      return outputModel.endOffset
    }

    // The history is monotonic in offset, so the first change after the stamp already holds the minimum changed offset.
    val offset = changesHistory[nextChangeIndex].startOffset
    // Clamp to the current end offset. The recorded change offsets can become stale when the
    // document shrinks (for example, `clear`) after a change was recorded but before the next flush updates the history.
    return minOf(offset, outputModel.endOffset)
  }

  private fun recordChange(startLine: TerminalLineIndex) {
    val offset = outputModel.getStartOfLine(startLine)
    val changeInfo = ChangeInfo(offset, outputModel.modificationStamp)
    // Keep the history monotonic in offset (sliding-window minimum)
    while (changesHistory.isNotEmpty() && changesHistory.last().startOffset >= offset) {
      changesHistory.removeLast()
    }
    changesHistory.addLast(changeInfo)
    while (changesHistory.size > MAX_CHANGES_HISTORY_LENGTH) {
      lastEvictedStamp = changesHistory.removeFirst().modificationStamp
    }
  }

  private data class ChangeInfo(
    // The offset of the first changed character
    val startOffset: TerminalOffset,
    // The modification stamp of the document at the moment of registering the change
    val modificationStamp: Long,
  )

  companion object {
    @VisibleForTesting
    const val MAX_CHANGES_HISTORY_LENGTH: Int = 200
  }
}

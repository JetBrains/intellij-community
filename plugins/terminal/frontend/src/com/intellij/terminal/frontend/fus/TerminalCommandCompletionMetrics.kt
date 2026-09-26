package com.intellij.terminal.frontend.fus

import org.jetbrains.annotations.ApiStatus

/** Collects completion and command-input lengths for one command. */
@ApiStatus.Internal
class TerminalCommandCompletionMetrics {
  private var totalCommandInsertedLength: Int = 0
  private var popupCompletionLength: Int = 0
  private var inlineCompletionLength: Int = 0
  private var typingsCount: Int = 0
  private var backspacesCount: Int = 0
  private var commandTypingStartedAt: Long? = null

  /** Counts inserted command text; removals do not affect the metric. */
  fun recordCommandTextInserted(length: Int, timeMillis: Long) {
    val insertedLength = length.coerceAtLeast(0)
    totalCommandInsertedLength += insertedLength
    if (insertedLength > 0) {
      commandTypingStartedAt = commandTypingStartedAt ?: timeMillis
    }
  }

  fun recordPopupInserted(length: Int) {
    if (length > 0) popupCompletionLength += length
  }

  fun recordInlineInserted(length: Int) {
    if (length > 0) inlineCompletionLength += length
  }

  fun recordTyping() {
    typingsCount++
  }

  fun recordBackspace() {
    if (totalCommandInsertedLength == 0) return

    backspacesCount++
  }

  fun takeAndReset(timeMillis: Long = System.currentTimeMillis()): CompletionLengthSnapshot {
    return CompletionLengthSnapshot(
      totalCommandInsertedLength = totalCommandInsertedLength,
      popupCompletionLength = popupCompletionLength,
      inlineCompletionLength = inlineCompletionLength,
      typingsCount = typingsCount,
      backspacesCount = backspacesCount,
      commandTypingTimeMillis = commandTypingStartedAt?.let {
        (timeMillis - it).coerceAtLeast(0)
      } ?: 0,
    ).also {
      reset()
    }
  }

  fun reset() {
    totalCommandInsertedLength = 0
    popupCompletionLength = 0
    inlineCompletionLength = 0
    typingsCount = 0
    backspacesCount = 0
    commandTypingStartedAt = null
  }

  data class CompletionLengthSnapshot(
    val totalCommandInsertedLength: Int,
    val popupCompletionLength: Int,
    val inlineCompletionLength: Int,
    val typingsCount: Int,
    val backspacesCount: Int,
    val commandTypingTimeMillis: Long,
  )
}

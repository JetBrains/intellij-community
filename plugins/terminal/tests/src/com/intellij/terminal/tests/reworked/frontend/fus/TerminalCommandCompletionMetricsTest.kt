package com.intellij.terminal.tests.reworked.frontend.fus

import com.intellij.terminal.frontend.fus.TerminalCommandCompletionMetrics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TerminalCommandCompletionMetricsTest {
  @Test
  fun `counts initial typed command`() {
    val metrics = TerminalCommandCompletionMetrics()

    metrics.recordCommandTextInserted(4)

    assertThat(metrics.takeAndReset()).isEqualTo(snapshot(totalCommandInsertedLength = 4))
  }

  @Test
  fun `counts typed characters appended to command`() {
    val metrics = TerminalCommandCompletionMetrics()

    metrics.recordCommandTextInserted(8)

    assertThat(metrics.takeAndReset()).isEqualTo(snapshot(totalCommandInsertedLength = 8))
  }

  @Test
  fun `does not subtract deleted text`() {
    val metrics = TerminalCommandCompletionMetrics()

    metrics.recordCommandTextInserted(12)
    metrics.recordCommandTextInserted(-6)

    assertThat(metrics.takeAndReset()).isEqualTo(snapshot(totalCommandInsertedLength = 12))
  }

  @Test
  fun `counts popup completion separately`() {
    val metrics = TerminalCommandCompletionMetrics()

    metrics.recordPopupInserted(10)

    assertThat(metrics.takeAndReset()).isEqualTo(snapshot(popupCompletionLength = 10))
  }

  @Test
  fun `counts inline completion separately`() {
    val metrics = TerminalCommandCompletionMetrics()

    metrics.recordInlineInserted(3)

    assertThat(metrics.takeAndReset()).isEqualTo(snapshot(inlineCompletionLength = 3))
  }

  @Test
  fun `counts typing and backspace events`() {
    val metrics = TerminalCommandCompletionMetrics()

    metrics.recordCommandTextInserted(1)
    metrics.recordTyping(100)
    metrics.recordTyping(150)
    metrics.recordBackspace(200)

    assertThat(metrics.takeAndReset(350)).isEqualTo(
      snapshot(totalCommandInsertedLength = 1, typingsCount = 2, backspacesCount = 1, commandTypingTimeMillis = 250)
    )
  }

  @Test
  fun `does not count backspace for an empty command`() {
    val metrics = TerminalCommandCompletionMetrics()

    metrics.recordBackspace(100)

    assertThat(metrics.takeAndReset(200)).isEqualTo(snapshot())
  }

  @Test
  fun `resets metrics for next command`() {
    val metrics = TerminalCommandCompletionMetrics()

    metrics.recordCommandTextInserted(4)
    metrics.recordPopupInserted(10)
    metrics.recordInlineInserted(3)
    metrics.takeAndReset()

    assertThat(metrics.takeAndReset()).isEqualTo(snapshot())
  }

  private fun snapshot(
    totalCommandInsertedLength: Int = 0,
    popupCompletionLength: Int = 0,
    inlineCompletionLength: Int = 0,
    typingsCount: Int = 0,
    backspacesCount: Int = 0,
    commandTypingTimeMillis: Long? = null,
  ): TerminalCommandCompletionMetrics.CompletionLengthSnapshot {
    return TerminalCommandCompletionMetrics.CompletionLengthSnapshot(
      totalCommandInsertedLength = totalCommandInsertedLength,
      popupCompletionLength = popupCompletionLength,
      inlineCompletionLength = inlineCompletionLength,
      typingsCount = typingsCount,
      backspacesCount = backspacesCount,
      commandTypingTimeMillis = commandTypingTimeMillis,
    )
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.terminal.tests.reworked.util.TerminalOutputEventCollector
import com.intellij.terminal.tests.reworked.util.awaitEvent
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.junit.Test

/**
 * Verifies that the Ghostty-backed [com.intellij.terminal.frontend.session.ghostty.GhosttyTerminalSession]
 * treats a soft-wrapped line as *one* logical line.
 *
 * A line longer than the terminal is stored as several grid rows, but the output document must show it as a single
 * line: the session joins rows without a `'\n'` while [com.intellij.terminal.emulator.TerminalRow.wrapped] is set,
 * and derives the cursor's logical line and column the same way. The flag comes from the emulator
 * (`ghostty_row_get(GHOSTTY_ROW_DATA_WRAP)`); when it is not populated, every wrapped row becomes its own document
 * line, the logical line index runs ahead of the real one, and the cursor column collapses to the position within
 * the last grid row. These tests fail in exactly that way if the flag regresses to a constant `false`.
 *
 * The terminal is the default 80x24, so a 200-character line occupies three rows (80 + 80 + 40).
 */
internal class GhosttyTerminalSessionWrappedLinesTest : GhosttyTerminalSessionTestCase() {

  @Test
  fun `a soft-wrapped line is one logical document line`() = runSessionTest { _, connector, collector ->
    val line = longLine(200)
    connector.feed(line)

    collector.awaitLastChunk(line)

    // Three grid rows, one logical line.
    assertThat(collector.documentLines()).containsExactly(line)
  }

  @Test
  fun `the cursor on a soft-wrapped line reports the logical line and column`() = runSessionTest { _, connector, collector ->
    val line = longLine(200)
    connector.feed(line)

    val event = collector.awaitLastChunk(line)

    // The cursor sits on grid row 2, column 40 — logically it is still on line 0, column 200.
    assertThat(event.cursorLogicalLineIndex).describedAs("cursor logical line").isEqualTo(0L)
    assertThat(event.cursorColumnIndex).describedAs("cursor logical column").isEqualTo(200)
  }

  @Test
  fun `wrapped lines scrolling into history advance the logical index by one line each`() = runSessionTest { _, connector, collector ->
    // 40 lines of three rows each = 120 grid rows on a 24-row screen, so most of them scroll into history and the
    // events have to keep counting logical lines, not rows.
    // Each line ends with its own marker so awaiting the tail of the last one cannot match an earlier event.
    val lines = (0 until 40).map { "line$it-" + longLine(180) + "-end$it" }
    connector.feed(lines.joinToString("\r\n"))

    val event = collector.awaitLastChunk(lines.last())

    assertThat(collector.documentLines()).isEqualTo(lines)
    assertThat(event.cursorLogicalLineIndex).describedAs("cursor logical line").isEqualTo(lines.size - 1L)
  }

  /** A [length]-character line of repeating letters — long enough to soft-wrap on the 80-column test terminal. */
  private fun longLine(length: Int): String = (0 until length).map { 'a' + it % 26 }.joinToString("")

  /**
   * Waits for the content event that carries the tail of [line]. The predicate matches the raw text, which is the
   * same whether or not the rows were joined, so a regression fails on the assertions that follow rather than
   * timing out here.
   */
  private suspend fun TerminalOutputEventCollector.awaitLastChunk(line: String): TerminalContentUpdatedEvent =
    awaitEvent<TerminalContentUpdatedEvent> { it.text.endsWith(line.takeLast(TAIL_ROW_LENGTH)) }

  private companion object {
    /** Characters of a wrapped line that land on its last grid row (200 and 197 both leave 40 or fewer). */
    const val TAIL_ROW_LENGTH = 30
  }
}

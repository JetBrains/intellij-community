// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.tests.reworked.util.awaitEvent
import com.intellij.terminal.tests.reworked.util.awaitEventAfter
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalResizeEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.junit.Assume
import org.junit.Test

/**
 * Tests terminal text-buffer modifications observed through [TerminalSession.getOutputFlow]: line wrapping/reflow,
 * scrollback growth and overflow, and alternate-screen-buffer switching by full-screen programs.
 *
 * Every case runs on both VT emulators — see [TerminalSessionTestCase].
 */
internal class TerminalTextBufferEventsTest(emulatorType: TerminalEmulatorType) : TerminalSessionTestCase(emulatorType) {

  // ---------------------------------------------------------------------------
  // (1) Reflow when text exceeds the terminal width
  // ---------------------------------------------------------------------------

  @Test
  fun `line wider than the terminal is reflowed and preserved across resizes`() = runSessionTest { session, connector, collector ->
    // 100 characters on an 80-column terminal wrap onto a second row.
    connector.feed("A".repeat(100))
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.count { c -> c == 'A' } == 100 }

    // Reflow narrower: the 100 characters re-wrap onto more rows but none are lost.
    var mark = collector.currentEventCount()
    session.getInputChannel().send(TerminalResizeEvent(TerminalGridSize(columns = 40, rows = 24)))
    val narrow = collector.awaitEventAfter<TerminalContentUpdatedEvent>(mark) { it.text.count { c -> c == 'A' } == 100 }
    assertThat(narrow.text.count { it == 'A' }).isEqualTo(100)

    // Reflow wider: they fit on a single row again, still all present.
    mark = collector.currentEventCount()
    session.getInputChannel().send(TerminalResizeEvent(TerminalGridSize(columns = 200, rows = 24)))
    val wide = collector.awaitEventAfter<TerminalContentUpdatedEvent>(mark) { it.text.count { c -> c == 'A' } == 100 }
    assertThat(wide.text.count { it == 'A' }).isEqualTo(100)
    // Across both reflows the document must end up with each character exactly once.
    assertThat(collector.documentText().count { it == 'A' })
      .describedAs("reflow must neither lose nor duplicate characters in the document")
      .isEqualTo(100)
  }

  // ---------------------------------------------------------------------------
  // (2) Adding text to the scrollback
  // ---------------------------------------------------------------------------

  @Test
  fun `output beyond the screen height is retained in the scrollback`() = runSessionTest { _, connector, collector ->
    // The terminal is 24 rows tall; 40 lines push the first 16 above the visible screen into scrollback.
    connector.feed((0 until 40).joinToString("\r\n") { "L%02d".format(it) })

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> {
      it.cursorLogicalLineIndex == 39L && it.text.contains("L39")
    }
    // The earliest line (now in the scrollback, above the screen) is still reported.
    assertThat(event.text).contains("L00")
    assertThat(event.text).contains("L39")
    assertThat(event.cursorLogicalLineIndex).isEqualTo(39L)
    assertThat(collector.documentLines()).isEqualTo((0 until 40).map { "L%02d".format(it) })
  }

  @Test
  fun `a later increment reports only the new tail, not a full resend`() = runSessionTest { _, connector, collector ->
    // 24-row screen: 40 lines push the first 16 into scrollback.
    connector.feed((0 until 40).joinToString("\r\n") { "L%02d".format(it) })
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.cursorLogicalLineIndex == 39L }

    val mark = collector.currentEventCount()
    connector.feed("\r\n" + (40 until 45).joinToString("\r\n") { "L%02d".format(it) })
    val second = collector.awaitEventAfter<TerminalContentUpdatedEvent>(mark) { it.cursorLogicalLineIndex == 44L }

    assertThat(second.startLineLogicalIndex).isGreaterThan(0L)
    assertThat(second.text).doesNotContain("L00")
    assertThat(collector.documentLines()).isEqualTo((0 until 45).map { "L%02d".format(it) })
  }

  @Test
  fun `a logical line wrapped across the scrollback boundary survives intact`() = runSessionTest { _, connector, collector ->
    // Logical line 0 is two 80-column rows. 30 more lines scroll both rows into history.
    val wrapped = "X".repeat(160)
    connector.feed(wrapped + "\r\n" + (0 until 30).joinToString("\r\n") { "Y%02d".format(it) })

    collector.awaitEvent<TerminalContentUpdatedEvent> { it.cursorLogicalLineIndex == 30L }

    val lines = collector.documentLines()
    assertThat(lines).hasSize(31)
    assertThat(lines[0]).isEqualTo(wrapped) // not split into two 80-char rows, not truncated
    assertThat(lines.drop(1)).isEqualTo((0 until 30).map { "Y%02d".format(it) })
  }

  @Test
  fun `resize that shrinks the screen moves rows into scrollback`() = runSessionTest { session, connector, collector ->
    // All 15 lines fit the 24-row screen: nothing is in scrollback yet.
    connector.feed((0 until 15).joinToString("\r\n") { "R%02d".format(it) })
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.cursorLogicalLineIndex == 14L }

    val mark = collector.currentEventCount()
    // JediTerm's change tracker reacts to a width change or new cell content, not a pure row-count shrink -
    // and a bare "\r\n" into a blank line is not new content either. Ghostty has no such gap; see below.
    session.getInputChannel().send(TerminalResizeEvent(TerminalGridSize(columns = 80, rows = 5)))
    connector.feed("\r\nz")
    collector.awaitEventAfter<TerminalContentUpdatedEvent>(mark) { it.text.contains("R14") }

    assertThat(collector.documentLines().dropLast(1)).isEqualTo((0 until 15).map { "R%02d".format(it) })
  }

  @Test
  fun `Ghostty reports a screen-shrinking resize immediately, without a follow-up write`() = runSessionTest { session, connector, collector ->
    // Ghostty's projector re-reads scrollbackRows every poll tick, so a resize alone is enough here, unlike
    // JediTerm above. Ghostty-only: a strictly stronger guarantee, not a required cross-emulator one.
    assumeGhostty()

    connector.feed((0 until 15).joinToString("\r\n") { "R%02d".format(it) })
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.cursorLogicalLineIndex == 14L }

    val mark = collector.currentEventCount()
    session.getInputChannel().send(TerminalResizeEvent(TerminalGridSize(columns = 80, rows = 5)))
    collector.awaitEventAfter<TerminalContentUpdatedEvent>(mark) { it.text.contains("R14") }

    assertThat(collector.documentLines()).isEqualTo((0 until 15).map { "R%02d".format(it) })
  }

  // ---------------------------------------------------------------------------
  // (3) Exceeding the scrollback size — oldest lines are dropped
  // ---------------------------------------------------------------------------

  @Test
  fun `oldest lines are dropped once the scrollback limit is exceeded`() {
    // JediTerm reports discarded history via a growing startLineLogicalIndex. Ghostty resets to index 0
    // instead (see the burst test below); its own overflow is covered by the emulator module's ScrollingTest.
    assumeJediTerm()
    // Shrink the scrollback so the overflow is reached with a small number of lines.
    setMaxScrollbackLines(100)

    runSessionTest { _, connector, collector ->
      connector.feed((0 until 400).joinToString("\r\n") { "L%03d".format(it) })

      // A content update starting past logical line 0 means the oldest lines have been discarded from the buffer.
      val discarded = collector.awaitEvent<TerminalContentUpdatedEvent> { it.startLineLogicalIndex > 0L }
      assertThat(discarded.startLineLogicalIndex).isGreaterThan(0L)
    }
  }

  @Test
  fun `document tail stays complete while the scrollback keeps evicting`() {
    // Ghostty can drop already-shown history under a fast burst instead of blocking writes (see the burst
    // test below); JediTerm has no such tradeoff, so this is JediTerm-only.
    assumeJediTerm()
    setMaxScrollbackLines(100)

    runSessionTest { _, connector, collector ->
      // (1) Overflow the scrollback of both emulators, so everything after this runs under eviction.
      connector.feed((1..PREFILL_LINES).joinToString("") { "prefill-$it\r\n" })
      collector.awaitEvent<TerminalContentUpdatedEvent> { it.cursorLogicalLineIndex == PREFILL_LINES.toLong() }

      // (2) Print lines 1..N, in feeds small enough that JediTerm's own eviction accounting keeps the
      // event stream complete between them.
      for (batchStart in 1..TAIL_LINES step FEED_BATCH_LINES) {
        connector.feed((batchStart until batchStart + FEED_BATCH_LINES).joinToString("") { "$it\r\n" })
      }
      collector.awaitEvent<TerminalContentUpdatedEvent> {
        it.cursorLogicalLineIndex == (PREFILL_LINES + TAIL_LINES).toLong()
      }

      // (3) The last N document lines are exactly 1..N: eviction dropped only lines older than them.
      // Compared manually so a failure reports the first mismatch, not two 100k-element lists.
      val expected = (1..TAIL_LINES).map { it.toString() }
      val tail = collector.documentLines()
        .dropLast(1) // the trailing newline leaves an empty last line
        .takeLast(expected.size)
      assertThat(tail).hasSize(expected.size)
      val mismatch = tail.zip(expected).indexOfFirst { (actual, wanted) -> actual != wanted }
      assertThat(mismatch)
        .describedAs("first mismatching tail line: expected '${expected.getOrNull(mismatch)}', found '${tail.getOrNull(mismatch)}'")
        .isEqualTo(-1)
    }
  }

  @Test
  fun `a burst that outpaces the scrollback cap resets to the visible tail, then tracking resumes normally`() {
    // Ghostty counterpart of `document tail stays complete...` above: it resets instead of blocking writes.
    assumeGhostty()

    runSessionTest { _, connector, collector ->
      connector.feed((0 until 10).joinToString("\r\n") { "BEFORE$it" })
      collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("BEFORE9") }

      // ~2000 blank lines fit in one PTY read (4096-char buffer), so this is one atomic emulator.write() -
      // an overflow with no timing race against the poll.
      connector.feed("\r\n" + "\r\n".repeat(2_000))
      collector.awaitEvent<TerminalContentUpdatedEvent> { it.startLineLogicalIndex == 0L }

      // The index only ever grows or resets to 0, never rewinds partway.
      val updates = collector.contentUpdates()
      for (i in 1 until updates.size) {
        assertThat(updates[i].startLineLogicalIndex >= updates[i - 1].startLineLogicalIndex || updates[i].startLineLogicalIndex == 0L)
          .describedAs("event $i: index went from ${updates[i - 1].startLineLogicalIndex} to ${updates[i].startLineLogicalIndex} without a reset")
          .isTrue()
      }

      // Recovery may take one more reset if the scrollback is still settling; the end result must be correct either way.
      val mark = collector.currentEventCount()
      connector.feed("AFTER1\r\nAFTER2")
      collector.awaitEventAfter<TerminalContentUpdatedEvent>(mark) { it.text.contains("AFTER2") }
      assertThat(collector.documentLines().takeLast(2)).isEqualTo(listOf("AFTER1", "AFTER2"))
    }
  }

  // ---------------------------------------------------------------------------
  // (4) Alternate screen buffer — scenarios of popular full-screen programs
  // ---------------------------------------------------------------------------

  @Test
  fun `vim-style editor uses the alternate screen and returns to the primary buffer on exit`() = runSessionTest { _, connector, collector ->
    connector.feed("user@host:~$ vim")
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("vim") }

    // Enter the alternate screen (DECSET 1049 = save cursor + switch to a cleared alternate buffer) and draw the editor.
    connector.feed("${ESC}[?1049h")
    collector.awaitEvent<TerminalStateChangedEvent> { it.state.isAlternateScreenBuffer }
    connector.feed("~ VIM ~\r\n\"file.txt\" 1L, 0B")
    val editor = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("VIM") }
    // The primary shell output is hidden (kept intact on the primary buffer), not shown on the alternate one.
    assertThat(editor.text)
      .describedAs("The alternate buffer must not show the primary (shell) output")
      .doesNotContain("user@host")
    assertThat(collector.alternateBufferText()).contains("VIM").doesNotContain("user@host")
    assertThat(collector.documentText().trimEnd())
      .describedAs("The editor frame must not leak into the primary (shell) document")
      .isEqualTo("user@host:~$ vim")

    // Exit vim (DECRST 1049): the terminal switches back to the primary (shell) buffer.
    val mark = collector.currentEventCount()
    connector.feed("${ESC}[?1049l")
    val exited = collector.awaitEventAfter<TerminalStateChangedEvent>(mark) { !it.state.isAlternateScreenBuffer }
    assertThat(exited.state.isAlternateScreenBuffer).isFalse()
  }

  @Test
  fun `htop-style app repaints the alternate screen`() = runSessionTest { _, connector, collector ->
    connector.feed("${ESC}[?1049h")
    collector.awaitEvent<TerminalStateChangedEvent> { it.state.isAlternateScreenBuffer }

    connector.feed("CPU 10%")
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("CPU 10%") }

    // Periodic refresh: clear the screen, home the cursor, and repaint with new values.
    connector.feed("${ESC}[2J${ESC}[HCPU 90%")
    val repaint = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("CPU 90%") && !it.text.contains("CPU 10%") }
    assertThat(repaint.text).contains("CPU 90%")
    assertThat(repaint.text).doesNotContain("CPU 10%")
    // The repaint replaces the whole alternate document: the old frame must be gone from it.
    assertThat(collector.alternateBufferText().trimEnd()).isEqualTo("CPU 90%")
  }

  @Test
  fun `legacy full-screen apps switch to the alternate buffer via modes 47 and 1047`() = runSessionTest { _, connector, collector ->
    // Mode 47 — the original alternate-screen switch (older curses apps).
    connector.feed("${ESC}[?47h")
    collector.awaitEvent<TerminalStateChangedEvent> { it.state.isAlternateScreenBuffer }
    var mark = collector.currentEventCount()
    connector.feed("${ESC}[?47l")
    collector.awaitEventAfter<TerminalStateChangedEvent>(mark) { !it.state.isAlternateScreenBuffer }

    // Mode 1047 — alternate screen with an implicit clear on exit.
    mark = collector.currentEventCount()
    connector.feed("${ESC}[?1047h")
    collector.awaitEventAfter<TerminalStateChangedEvent>(mark) { it.state.isAlternateScreenBuffer }
    mark = collector.currentEventCount()
    connector.feed("${ESC}[?1047l")
    collector.awaitEventAfter<TerminalStateChangedEvent>(mark) { !it.state.isAlternateScreenBuffer }
  }

  // ---------------------------------------------------------------------------
  // Harness (mirrors TerminalSessionOutputEventsTest)
  // ---------------------------------------------------------------------------

  private fun assumeJediTerm() {
    Assume.assumeTrue("Not applicable to the Ghostty emulator", emulatorType == TerminalEmulatorType.JediTerm)
  }

  private fun assumeGhostty() {
    Assume.assumeTrue("Not applicable to the JediTerm emulator", emulatorType == TerminalEmulatorType.Ghostty)
  }

  /** Shrinks the JediTerm scrollback cap until the end of the test; the Ghostty cap is not configurable. */
  private fun setMaxScrollbackLines(count: Int) {
    val maxLinesKey = "terminal.buffer.max.lines.count"
    val previousMaxLines = AdvancedSettings.getInt(maxLinesKey)
    AdvancedSettings.setInt(maxLinesKey, count)
    Disposer.register(disposable) { AdvancedSettings.setInt(maxLinesKey, previousMaxLines) }
  }

  companion object {
    /** Escape (0x1B): introduces CSI/OSC control sequences. */
    private val ESC: String = Char(0x1B).toString()

    /** Enough lines to overflow both emulators' (shrunken, see [setMaxScrollbackLines]) scrollback caps. */
    private const val PREFILL_LINES = 2_000

    /** How many trailing lines must survive sustained eviction intact. */
    private const val TAIL_LINES = 100_000

    /**
     * Lines per feed while printing the tail (JediTerm-only, see `document tail stays complete while the
     * scrollback keeps evicting`): small enough that JediTerm's own eviction accounting stays exact between
     * feeds.
     */
    private const val FEED_BATCH_LINES = 250
  }
}

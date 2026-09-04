// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.frontend.view.activeOutputModel
import com.intellij.terminal.tests.reworked.util.ESC
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.text
import com.intellij.terminal.tests.reworked.util.TerminalViewTestCase
import com.intellij.terminal.tests.reworked.util.assertOutputModelState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Tests terminal text-buffer modifications observed through the real [TerminalOutputModel]: line wrapping/reflow,
 * scrollback growth and overflow, and alternate-screen-buffer switching by full-screen programs.
 *
 * Every case runs on both VT emulators, see [TerminalViewTestCase].
 */
internal class TerminalTextBufferEventsTest(emulatorType: TerminalEmulatorType) : TerminalViewTestCase(emulatorType) {

  // ---------------------------------------------------------------------------
  // (1) Reflow when text exceeds the terminal width
  // ---------------------------------------------------------------------------

  @Test
  fun `line wider than the terminal is reflowed and preserved across resizes`() = doTest { fixture ->
    val model = fixture.view.activeOutputModel()

    // 100 characters on an 80-column terminal wrap onto a second row.
    fixture.connector.feed("A".repeat(100))
    fixture.assertOutputModelState(model) { it.text.count { c -> c == 'A' } == 100 }

    // Reflow narrower: the 100 characters re-wrap onto more rows but none are lost.
    fixture.resizeAndAwait(columns = 40, rows = 24)
    assertThat(model.text.count { it == 'A' })
      .describedAs("a narrowing reflow must neither lose nor duplicate characters")
      .isEqualTo(100)

    // Reflow wider: they fit on a single row again, still all present.
    fixture.resizeAndAwait(columns = 200, rows = 24)
    assertThat(model.text.count { it == 'A' })
      .describedAs("a widening reflow must neither lose nor duplicate characters")
      .isEqualTo(100)
  }

  // ---------------------------------------------------------------------------
  // (1b) A soft-wrapped line stays one logical document line
  // ---------------------------------------------------------------------------

  @Test
  fun `a soft-wrapped line is one logical document line`() = doTest { fixture ->
    val model = fixture.view.activeOutputModel()

    // The terminal is 80 columns wide; 200 characters occupy three rows (80 + 80 + 40) but stay one logical line.
    val line = longLine(200)
    fixture.connector.feed(line)
    fixture.assertOutputModelState(model) { it.text == line }

    assertThat(model.text.split("\n")).containsExactly(line)
  }

  @Test
  fun `the cursor on a soft-wrapped line reports the logical line and column`() = doTest { fixture ->
    val model = fixture.view.activeOutputModel()

    val line = longLine(200)
    fixture.connector.feed(line)
    fixture.assertOutputModelState(model) { it.text == line }

    // The cursor sits on grid row 2, column 40 — logically it is still on line 0, column 200.
    assertThat(model.cursorLine()).isEqualTo(0L)
    assertThat(model.cursorColumn()).isEqualTo(200L)
  }

  @Test
  fun `wrapped lines scrolling into history advance the logical index by one line each`() = doTest { fixture ->
    val model = fixture.view.activeOutputModel()

    // 40 lines of three rows each = 120 grid rows on a 24-row screen, so most of them scroll into history and
    // the model has to keep counting logical lines, not rows.
    val lines = (0 until 40).map { "line$it-" + longLine(180) + "-end$it" }
    fixture.connector.feed(lines.joinToString("\r\n"))
    fixture.assertOutputModelState(model) { it.cursorLine() == lines.size - 1L }

    assertThat(model.text.split("\n")).isEqualTo(lines)
  }

  // ---------------------------------------------------------------------------
  // (2) Adding text to the scrollback
  // ---------------------------------------------------------------------------

  @Test
  fun `output beyond the screen height is retained in the scrollback`() = doTest { fixture ->
    val model = fixture.view.activeOutputModel()

    // The terminal is 24 rows tall; 40 lines push the first 16 above the visible screen into scrollback.
    fixture.connector.feed((0 until 40).joinToString("\r\n") { "L%02d".format(it) })
    fixture.assertOutputModelState(model) { it.cursorLine() == 39L && it.text.contains("L39") }

    // The earliest line (now in the scrollback, above the screen) is still reported.
    assertThat(model.text).contains("L00")
    assertThat(model.text).contains("L39")
    assertThat(model.cursorLine()).isEqualTo(39L)
    assertThat(model.text.split("\n")).isEqualTo((0 until 40).map { "L%02d".format(it) })
  }

  @Test
  fun `a later increment reports only the new tail, not a full resend`() = doTest { fixture ->
    val model = fixture.view.activeOutputModel()

    // 24-row screen: 40 lines push the first 16 into scrollback.
    fixture.connector.feed((0 until 40).joinToString("\r\n") { "L%02d".format(it) })
    fixture.assertOutputModelState(model) { it.cursorLine() == 39L }

    val changes = mutableListOf<TerminalContentChangeEvent>()
    val listenerDisposable = Disposer.newDisposable()
    model.addListener(listenerDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        changes.add(event)
      }
    })
    fixture.connector.feed("\r\n" + (40 until 45).joinToString("\r\n") { "L%02d".format(it) })
    fixture.assertOutputModelState(model) { it.cursorLine() == 44L }
    Disposer.dispose(listenerDisposable)

    // A change starting at absolute offset 0, or one whose new text still mentions "L00", would mean the
    // whole buffer was resent instead of just the new tail.
    assertThat(changes).isNotEmpty()
    assertThat(changes.map { it.offset.toAbsolute() }).allMatch { it > 0L }
    assertThat(changes.map { it.newText.toString() }).noneMatch { it.contains("L00") }
    assertThat(model.text.split("\n")).isEqualTo((0 until 45).map { "L%02d".format(it) })
  }

  @Test
  fun `a logical line wrapped across the scrollback boundary survives intact`() = doTest { fixture ->
    val model = fixture.view.activeOutputModel()

    // Logical line 0 is two 80-column rows. 30 more lines scroll both rows into history.
    val wrapped = "X".repeat(160)
    fixture.connector.feed(wrapped + "\r\n" + (0 until 30).joinToString("\r\n") { "Y%02d".format(it) })
    fixture.assertOutputModelState(model) { it.cursorLine() == 30L }

    val lines = model.text.split("\n")
    assertThat(lines).hasSize(31)
    assertThat(lines[0]).isEqualTo(wrapped) // not split into two 80-char rows, not truncated
    assertThat(lines.drop(1)).isEqualTo((0 until 30).map { "Y%02d".format(it) })
  }

  @Test
  fun `resize that shrinks the screen moves rows into scrollback`() = doTest { fixture ->
    val model = fixture.view.activeOutputModel()

    // All 15 lines fit the 24-row screen: nothing is in scrollback yet.
    fixture.connector.feed((0 until 15).joinToString("\r\n") { "R%02d".format(it) })
    fixture.assertOutputModelState(model) { it.cursorLine() == 14L }

    // JediTerm's change tracker reacts to a width change or new cell content, not a pure row-count shrink -
    // and a bare "\r\n" into a blank line is not new content either. Ghostty has no such gap; see below.
    fixture.resize(columns = 80, rows = 5)
    fixture.connector.feed("\r\nz")
    // "R14" is already in the text from the first feed; "z" is the only marker that the resize+write settled.
    fixture.assertOutputModelState(model) { it.text.contains("z") }

    assertThat(model.text.split("\n").dropLast(1)).isEqualTo((0 until 15).map { "R%02d".format(it) })
  }

  @Test
  fun `Ghostty reports a screen-shrinking resize immediately, without a follow-up write`() = doTest { fixture ->
    // Ghostty's projector re-reads scrollbackRows every poll tick, so a resize alone is enough here, unlike
    // JediTerm above. Ghostty-only: a strictly stronger guarantee, not a required cross-emulator one.
    assumeGhostty()
    val model = fixture.view.activeOutputModel()

    fixture.connector.feed((0 until 15).joinToString("\r\n") { "R%02d".format(it) })
    fixture.assertOutputModelState(model) { it.cursorLine() == 14L }

    // The resize doesn't change the reported content, so the only observable signal that it was reported
    // at all (rather than silently ignored until some later write) is a model update firing for it.
    val updateFired = CompletableDeferred<Unit>()
    val listenerDisposable = Disposer.newDisposable()
    model.addListener(listenerDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        updateFired.complete(Unit)
      }
    })
    try {
      fixture.resize(columns = 80, rows = 5)
      withTimeout(10.seconds) { updateFired.await() }
    }
    finally {
      Disposer.dispose(listenerDisposable)
    }

    assertThat(model.text.split("\n")).isEqualTo((0 until 15).map { "R%02d".format(it) })
  }

  @Test
  fun `a resize in both dimensions preserves every line`() = doTest { fixture ->
    // Ghostty only: it reports its own reflow, where JediTerm needs a following write. See above.
    assumeGhostty()
    val model = fixture.view.activeOutputModel()
    val lines = (0 until 40).map { "R%02d".format(it).padEnd(100, '-') }

    fixture.connector.feed(lines.joinToString("\r\n"))
    fixture.assertOutputModelState(model) { it.text.contains("R39") }

    // Narrower and shorter, then wider and taller. A soft wrap is not a line end, so neither reflow may
    // change the document text.
    fixture.resizeAndAwait(columns = 40, rows = 10)
    assertThat(model.text.split("\n")).describedAs("after a shrink in both dimensions").isEqualTo(lines)

    fixture.resizeAndAwait(columns = 200, rows = 40)
    assertThat(model.text.split("\n")).describedAs("after a growth in both dimensions").isEqualTo(lines)
  }

  @Test
  fun `the cursor keeps its position across a resize`() = doTest { fixture ->
    assumeGhostty()
    val model = fixture.view.activeOutputModel()

    // 30 lines of three characters: the cursor ends on line 29, at column 3.
    fixture.connector.feed((0 until 30).joinToString("\r\n") { "R%02d".format(it) })
    fixture.assertOutputModelState(model) { it.cursorLine() == 29L }
    assertThat(model.cursorColumn()).isEqualTo(3L)

    // A reflow moves the cursor's row, but not the logical line or the column it sits on.
    fixture.resizeAndAwait(columns = 80, rows = 5)
    assertThat(model.cursorLine()).describedAs("after a height shrink").isEqualTo(29L)
    assertThat(model.cursorColumn()).describedAs("after a height shrink").isEqualTo(3L)

    fixture.resizeAndAwait(columns = 40, rows = 24)
    assertThat(model.cursorLine()).describedAs("after a width shrink").isEqualTo(29L)
    assertThat(model.cursorColumn()).describedAs("after a width shrink").isEqualTo(3L)
  }

  @Test
  fun `a height growth does not clear an output the model already trimmed`() {
    assumeGhostty()
    // A 1 KB cap holds about 170 of these lines, so the model trims while the emulator still has more.
    // The model reads the cap when it is built, so this must run before the fixture exists.
    setMaxOutputCapacityKb(1)

    doTest { fixture ->
      val model = fixture.view.activeOutputModel()

      fixture.connector.feed((0 until 400).joinToString("\r\n") { "R%03d".format(it) })
      fixture.assertOutputModelState(model) { it.text.contains("R399") }
      assertThat(model.startOffset).describedAs("the model must have trimmed").isGreaterThan(TerminalOffset.ZERO)
      val trimmedTo = model.startOffset

      // A growth moves the reported anchor back by the rows it recovers from the scrollback. Nothing
      // clamps that against the trim watermark, and an anchor below it makes the model clear everything.
      fixture.resizeAndAwait(columns = 80, rows = 5)
      assertThat(model.text).describedAs("the tail must survive the shrink").contains("R399")
      fixture.resizeAndAwait(columns = 80, rows = 40)

      assertThat(model.text).describedAs("the tail must survive the growth").contains("R399")
      assertThat(model.startOffset).describedAs("trimming only ever moves forward").isGreaterThanOrEqualTo(trimmedTo)
    }
  }

  @Test
  fun `entering the alternate screen shows the program at the top of the alternate model`() = doTest { fixture ->
    assumeGhostty()
    val regular = fixture.view.outputModels.regular
    val alternate = fixture.view.outputModels.alternative

    // 60 lines leave 36 rows in the scrollback, which is more than the 24 screen rows.
    fixture.connector.feed((0 until 60).joinToString("\r\n") { "R%02d".format(it) })
    fixture.assertOutputModelState(regular) { it.text.contains("R59") }

    fixture.connector.feed("${ESC}[?1049h")
    fixture.view.sessionModel.terminalState.first { it.isAlternateScreenBuffer }

    // The switch alone keeps the cursor at its primary-screen row and column, so a real full-screen
    // program always positions it before its first draw - this does the same.
    fixture.connector.feed("${ESC}[H" + "~ VIM ~")
    fixture.assertOutputModelState(alternate) { it.text.contains("~ VIM ~") }

    // The alternate screen starts empty and holds no scrollback, so its model must hold the editor's
    // screen alone, with the program's first row on the first line.
    assertThat(alternate.text.split("\n").first()).describedAs("the first alternate line").isEqualTo("~ VIM ~")
  }

  @Test
  fun `a switch combined with its first draw in one chunk resolves correctly on both models`() = doTest { fixture ->
    assumeGhostty()
    val regular = fixture.view.outputModels.regular
    val alternate = fixture.view.outputModels.alternative

    // 60 lines leave real pre-switch content in the primary scrollback to recover once reactivated.
    val primaryLines = (0 until 60).map { "P%02d".format(it) }
    // The trailing primary content, the switch, and the program's first alternate-screen draw all
    // arrive in one chunk — the "switch buried mid-stream" case a byte-stream sniffer could not
    // reliably catch.
    fixture.connector.feed(primaryLines.joinToString("\r\n") + "${ESC}[?1049h" + "ALT1")
    fixture.assertOutputModelState(alternate) { it.text.contains("ALT1") }
    assertThat(regular.text).describedAs("the primary model must not show alternate-screen content").doesNotContain("ALT1")

    fixture.connector.feed("\r\nALT2")
    fixture.assertOutputModelState(alternate) { it.text.contains("ALT2") }
    assertThat(alternate.text).describedAs("the alternate model must not show primary content").doesNotContain("P59")

    fixture.connector.feed("${ESC}[?1049l")

    // The primary screen was frozen the whole time it was inactive, so one catch-up read on return
    // must recover it fully and correctly, with nothing lost from before the switch.
    fixture.assertOutputModelState(regular) { it.text.contains("P59") }
    assertThat(regular.text.split("\n")).isEqualTo(primaryLines)
  }

  @Test
  fun `a cursor move on the alternate screen moves only the alternate cursor`() = doTest { fixture ->
    assumeGhostty()
    val regular = fixture.view.outputModels.regular
    val alternate = fixture.view.outputModels.alternative

    // 60 lines push 36 rows into the primary scrollback, so the primary anchor sits well above 0. An
    // alternate-screen cursor computed against that anchor would land tens of lines too low.
    fixture.connector.feed((0 until 60).joinToString("\r\n") { "R%02d".format(it) })
    fixture.assertOutputModelState(regular) { it.text.contains("R59") }
    val regularCursorLine = regular.cursorLine()

    fixture.connector.feed("${ESC}[?1049h${ESC}[H~ VIM ~")
    fixture.assertOutputModelState(alternate) { it.text.contains("~ VIM ~") }

    // Row 3, column 5 of the alternate screen, with nothing drawn: the position has to be reported on
    // its own, against the alternate screen's own origin.
    fixture.connector.feed("${ESC}[3;5H")

    fixture.assertOutputModelState(alternate) { it.cursorLine() == 2L && it.cursorColumn() == 4L }
    assertThat(regular.cursorLine())
      .describedAs("the primary cursor must not follow the alternate screen")
      .isEqualTo(regularCursorLine)
  }

  @Test
  fun `a width shrink inside an alternate screen excursion keeps both models correct`() = doTest { fixture ->
    assumeGhostty()
    val regular = fixture.view.outputModels.regular
    val alternate = fixture.view.outputModels.alternative

    // 100-character lines take two rows each at 80 columns and three at 40, so the shrink below really
    // reflows the primary screen while nothing is watching it.
    val primaryLines = (0 until 60).map { "P%02d".format(it).padEnd(100, '-') }
    fixture.connector.feed(primaryLines.joinToString("\r\n"))
    fixture.assertOutputModelState(regular) { it.text.contains(primaryLines.last()) }

    fixture.connector.feed("${ESC}[?1049h${ESC}[HALT")
    fixture.assertOutputModelState(alternate) { it.text.contains("ALT") }

    // The user drags the window narrow while the full-screen program is up. Ghostty reflows both
    // screens; the primary reflow stays unreported until the program exits.
    fixture.resizeAndAwait(columns = 40, rows = 10)

    fixture.connector.feed("${ESC}[?1049l")

    // One catch-up read has to restore the primary screen whole, reflowed to the new width. This reflow
    // stays under HISTORY_REPLACE_LINES; for the shrink that does not, see
    // TerminalEmulatorOutputProjectorTest#a width shrink performed on the alternate screen keeps the
    // primary anchor on return.
    fixture.assertOutputModelState(regular) { it.text.contains(primaryLines.last()) }
    assertThat(regular.text.split("\n"))
      .describedAs("a reflow discovered on the way back must neither lose nor duplicate a line")
      .isEqualTo(primaryLines)
    assertThat(alternate.text)
      .describedAs("the alternate model must not pick up primary content")
      .doesNotContain("P59")
  }

  @Test
  fun `a switch that flips back inside one chunk reports neither the switch nor what it drew`() = doTest { fixture ->
    assumeGhostty()
    val regular = fixture.view.outputModels.regular
    val alternate = fixture.view.outputModels.alternative

    fixture.connector.feed("MAIN")
    fixture.assertOutputModelState(regular) { it.text.contains("MAIN") }

    // The whole round trip lands in one emulator.write, so no projection tick falls inside it. This is
    // the same-tick coalescing gap GhosttyTerminalSession documents: the state never changes, so neither
    // the switch nor the frame is reported. Pinned here so a change to it is a decision, not a surprise.
    fixture.connector.feed("${ESC}[?1049hGHOST${ESC}[?1049lAFTER")

    fixture.assertOutputModelState(regular) { it.text.contains("AFTER") }
    assertThat(alternate.text).describedAs("the discarded alternate frame is never reported").doesNotContain("GHOST")
    assertThat(regular.text.trimEnd()).isEqualTo("MAINAFTER")
    assertThat(fixture.view.sessionModel.terminalState.value.isAlternateScreenBuffer)
      .describedAs("the buffer flag never moved")
      .isFalse()
  }

  @Test
  fun `a resize inside an open synchronized output block still reaches the model`() = doTest { fixture ->
    assumeGhostty()
    val model = fixture.view.activeOutputModel()
    val lines = (0 until 40).map { "R%02d".format(it).padEnd(100, '-') }

    // DEC 2026 holds repaints back. Nothing closes this block, so every frame below reaches the model
    // only through the watchdog that bounds it (SYNC_OUTPUT_TIMEOUT, 1000 ms).
    fixture.connector.feed("${ESC}[?2026h" + lines.joinToString("\r\n"))
    fixture.assertOutputModelState(model, timeout = 30.seconds) { it.text.contains(lines.last()) }

    // A resize applies to the emulator at once, but its reflow is a frame like any other, so it is
    // deferred too. It must not be dropped: the block may never close.
    fixture.resizeAndAwait(columns = 40, rows = 10, timeout = 30.seconds)
    assertThat(model.text.split("\n")).describedAs("the reflow inside the block").isEqualTo(lines)

    fixture.connector.feed("${ESC}[?2026lTAIL")
    fixture.assertOutputModelState(model) { it.text.endsWith("TAIL") }
  }

  @Test
  fun `a resize while the output streams converges on the whole tail`() = doTest { fixture ->
    assumeGhostty()
    val model = fixture.view.activeOutputModel()

    // The read loop consumes the feed in 4096-character chunks, so the resize below lands somewhere in
    // the middle of it. Which frames the projector reports on the way is timing, and not assertable;
    // the state it settles on is.
    fixture.connector.feed((0 until 2_000).joinToString("\r\n") { "R%04d".format(it) })
    fixture.resize(columns = 40, rows = 10)

    fixture.assertOutputModelState(model, timeout = 30.seconds) {
      it.text.split("\n").takeLast(2) == listOf("R1998", "R1999")
    }
    assertThat(model.cursorLine())
      .describedAs("the cursor must settle on the last line the output produced")
      .isEqualTo(model.getLineByOffset(model.endOffset).toAbsolute())
  }

  // ---------------------------------------------------------------------------
  // (3) Exceeding the scrollback size — oldest lines are dropped
  // ---------------------------------------------------------------------------

  @Test
  fun `the output model retains lines that the emulator's own scrollback has already evicted`() {
    // JediTerm reports its content from wherever its own (shrunk) buffer currently starts; Ghostty resets to
    // index 0 instead under a burst (see the burst test below), so this is JediTerm-only.
    assumeJediTerm()
    // Shrink the emulator's own scrollback so its eviction is reached with a small number of lines. The output
    // model's own (much larger) capacity is untouched: updateContent() only replaces the reported window forward
    // from the emulator's own line index, so lines the emulator evicted from its own buffer stay in the model.
    setMaxScrollbackLines(100)

    doTest { fixture ->
      val model = fixture.view.activeOutputModel()
      fixture.connector.feed((0 until 400).joinToString("\r\n") { "L%03d".format(it) })

      fixture.assertOutputModelState(model) { it.cursorLine() == 399L }
      assertThat(model.text.split("\n")).isEqualTo((0 until 400).map { "L%03d".format(it) })
    }
  }

  @Test
  fun `document tail stays complete while the scrollback keeps evicting`() {
    // Ghostty can drop already-shown history under a fast burst instead of blocking writes (see the burst
    // test below); JediTerm has no such tradeoff, so this is JediTerm-only.
    assumeJediTerm()
    setMaxScrollbackLines(100)
    // The output model's own capacity must not be the bottleneck: only the emulator's scrollback should evict.
    setMaxOutputCapacityKb()

    doTest { fixture ->
      val model = fixture.view.activeOutputModel()

      // (1) Overflow the scrollback of both emulators, so everything after this runs under eviction.
      fixture.connector.feed((1..PREFILL_LINES).joinToString("") { "prefill-$it\r\n" })
      fixture.assertOutputModelState(model) { it.cursorLine() == PREFILL_LINES.toLong() }

      // (2) Print lines 1..N, in feeds small enough that JediTerm's own eviction accounting keeps the
      // event stream complete between them.
      for (batchStart in 1..TAIL_LINES step FEED_BATCH_LINES) {
        fixture.connector.feed((batchStart until batchStart + FEED_BATCH_LINES).joinToString("") { "$it\r\n" })
      }
      fixture.assertOutputModelState(model, timeout = 30.seconds) { it.cursorLine() == (PREFILL_LINES + TAIL_LINES).toLong() }

      // (3) The last N document lines are exactly 1..N: eviction dropped only lines older than them.
      // Compared manually so a failure reports the first mismatch, not two 100k-element lists.
      val expected = (1..TAIL_LINES).map { it.toString() }
      val tail = model.text.split("\n")
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

    doTest { fixture ->
      val model = fixture.view.activeOutputModel()
      fixture.connector.feed((0 until 10).joinToString("\r\n") { "BEFORE$it" })
      fixture.assertOutputModelState(model) { it.text.contains("BEFORE9") }

      val firstLineIndices = mutableListOf<Long>()
      val listenerDisposable = Disposer.newDisposable()
      model.addListener(listenerDisposable, object : TerminalOutputModelListener {
        override fun afterContentChanged(event: TerminalContentChangeEvent) {
          firstLineIndices.add(event.model.firstLineIndex.toAbsolute())
        }
      })

      // ~2000 blank lines fit in one PTY read (4096-char buffer), so this is one atomic emulator.write() -
      // an overflow with no timing race against the poll.
      fixture.connector.feed("\r\n" + "\r\n".repeat(2_000))
      fixture.assertOutputModelState(model) { it.firstLineIndex.toAbsolute() == 0L }
      Disposer.dispose(listenerDisposable)

      // The index only ever grows or resets to 0, never rewinds partway.
      for (i in 1 until firstLineIndices.size) {
        assertThat(firstLineIndices[i] >= firstLineIndices[i - 1] || firstLineIndices[i] == 0L)
          .describedAs("index went from ${firstLineIndices[i - 1]} to ${firstLineIndices[i]} without a reset")
          .isTrue()
      }

      // Recovery may take one more reset if the scrollback is still settling; the end result must be correct either way.
      fixture.connector.feed("AFTER1\r\nAFTER2")
      fixture.assertOutputModelState(model) { it.text.contains("AFTER2") }
      assertThat(model.text.split("\n").takeLast(2)).isEqualTo(listOf("AFTER1", "AFTER2"))
    }
  }

  @Test
  fun `Ghostty restores the history once a burst stops, without any further output`() {
    // A burst makes the projector report the screen alone, and it restores the history on the first
    // projection that finalizes nothing. Nothing is written after the burst here, so that projection happens
    // only because the session keeps projecting while the read is owed - otherwise the output stopping is
    // exactly what stops the projection that would notice it.
    assumeGhostty()

    doTest { fixture ->
      val model = fixture.view.activeOutputModel()
      fixture.connector.feed((0 until 2_000).joinToString("\r\n") { "line-$it" })

      fixture.assertOutputModelState(model) {
        it.firstLineIndex.toAbsolute() == 0L && it.text.startsWith("line-0\n") && it.text.contains("line-1999")
      }
      assertThat(model.text.split("\n").first()).isEqualTo("line-0")
      assertThat(model.text).contains("line-1999")
    }
  }

  // ---------------------------------------------------------------------------
  // (4) Alternate screen buffer — scenarios of popular full-screen programs
  // ---------------------------------------------------------------------------

  @Test
  fun `vim-style editor uses the alternate screen and returns to the primary buffer on exit`() = doTest { fixture ->
    val regular = fixture.view.outputModels.regular
    val alternate = fixture.view.outputModels.alternative

    fixture.connector.feed("user@host:~$ vim")
    fixture.assertOutputModelState(regular) { it.text.contains("vim") }

    // Enter the alternate screen (DECSET 1049 = save cursor + switch to a cleared alternate buffer) and draw the editor.
    fixture.connector.feed("${ESC}[?1049h")
    fixture.view.sessionModel.terminalState.first { it.isAlternateScreenBuffer }
    fixture.connector.feed("~ VIM ~\r\n\"file.txt\" 1L, 0B")
    fixture.assertOutputModelState(alternate) { it.text.contains("VIM") }
    // The primary shell output is hidden (kept intact on the primary buffer), not shown on the alternate one.
    assertThat(alternate.text)
      .describedAs("The alternate buffer must not show the primary (shell) output")
      .doesNotContain("user@host")
    assertThat(regular.text.trimEnd())
      .describedAs("The editor frame must not leak into the primary (shell) document")
      .isEqualTo("user@host:~$ vim")

    // Exit vim (DECRST 1049): the terminal switches back to the primary (shell) buffer.
    fixture.connector.feed("${ESC}[?1049l")
    val exited = fixture.view.sessionModel.terminalState.first { !it.isAlternateScreenBuffer }
    assertThat(exited.isAlternateScreenBuffer).isFalse()
  }

  @Test
  fun `htop-style app repaints the alternate screen`() = doTest { fixture ->
    val alternate = fixture.view.outputModels.alternative

    fixture.connector.feed("${ESC}[?1049h")
    fixture.view.sessionModel.terminalState.first { it.isAlternateScreenBuffer }

    fixture.connector.feed("CPU 10%")
    fixture.assertOutputModelState(alternate) { it.text.contains("CPU 10%") }

    // Periodic refresh: clear the screen, home the cursor, and repaint with new values.
    fixture.connector.feed("${ESC}[2J${ESC}[HCPU 90%")
    fixture.assertOutputModelState(alternate) { it.text.contains("CPU 90%") && !it.text.contains("CPU 10%") }
    // The repaint replaces the whole alternate document: the old frame must be gone from it.
    assertThat(alternate.text.trimEnd()).isEqualTo("CPU 90%")
  }

  @Test
  fun `legacy full-screen apps switch to the alternate buffer via modes 47 and 1047`() = doTest { fixture ->
    val terminalState = fixture.view.sessionModel.terminalState

    // Mode 47 — the original alternate-screen switch (older curses apps).
    fixture.connector.feed("${ESC}[?47h")
    terminalState.first { it.isAlternateScreenBuffer }
    fixture.connector.feed("${ESC}[?47l")
    terminalState.first { !it.isAlternateScreenBuffer }

    // Mode 1047 — alternate screen with an implicit clear on exit.
    fixture.connector.feed("${ESC}[?1047h")
    terminalState.first { it.isAlternateScreenBuffer }
    fixture.connector.feed("${ESC}[?1047l")
    terminalState.first { !it.isAlternateScreenBuffer }
  }

  // ---------------------------------------------------------------------------
  // Harness
  // ---------------------------------------------------------------------------

  /** Shrinks the JediTerm scrollback cap until the end of the test; the Ghostty cap is not configurable. */
  private fun setMaxScrollbackLines(count: Int) {
    val maxLinesKey = "terminal.buffer.max.lines.count"
    val previousMaxLines = AdvancedSettings.getInt(maxLinesKey)
    AdvancedSettings.setInt(maxLinesKey, count)
    Disposer.register(disposable) { AdvancedSettings.setInt(maxLinesKey, previousMaxLines) }
  }

  /**
   * Sets the output model's own character cap until the end of the test.
   */
  private fun setMaxOutputCapacityKb(kb: Int = 4096) {
    val capacityKey = "new.terminal.output.capacity.kb"
    val previousCapacity = AdvancedSettings.getInt(capacityKey)
    AdvancedSettings.setInt(capacityKey, kb)
    Disposer.register(disposable) { AdvancedSettings.setInt(capacityKey, previousCapacity) }
  }

  private fun TerminalOutputModel.cursorLine(): Long = getLineByOffset(cursorOffset).toAbsolute()

  private fun TerminalOutputModel.cursorColumn(): Long = cursorOffset - getStartOfLine(getLineByOffset(cursorOffset))

  /** A [length]-character line of repeating letters — long enough to soft-wrap on the 80-column test terminal. */
  private fun longLine(length: Int): String = (0 until length).map { 'a' + it % 26 }.joinToString("")

  companion object {
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

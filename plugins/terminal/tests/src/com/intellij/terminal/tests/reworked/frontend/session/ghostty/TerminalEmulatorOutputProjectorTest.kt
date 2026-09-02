// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.terminal.emulator.TerminalCustomCommandListener
import com.intellij.terminal.emulator.TerminalEmulator
import com.intellij.terminal.emulator.TerminalSize
import com.intellij.terminal.emulator.createTerminalEmulator
import com.intellij.terminal.frontend.session.ghostty.TerminalEmulatorOutputProjector
import com.intellij.terminal.tests.reworked.util.ESC
import com.intellij.terminal.tests.reworked.util.promptStartedOsc
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.CursorShapeDto
import org.jetbrains.plugins.terminal.session.impl.dto.MouseFormatDto
import org.jetbrains.plugins.terminal.session.impl.dto.MouseModeDto
import org.jetbrains.plugins.terminal.session.impl.dto.Osc8HyperlinkDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalStateDto
import org.jetbrains.plugins.terminal.session.impl.dto.TextStyleOptionDto
import org.junit.Ignore
import org.junit.Test

/**
 * Drives [TerminalEmulatorOutputProjector] directly: write VT bytes into an emulator, then call the projector,
 * all on the test thread. No session, no read loop, no polling - so a projection is exactly one call, and every
 * assertion is deterministic, unlike the session-level tests
 * ([com.intellij.terminal.tests.reworked.frontend.session.TerminalTextBufferEventsTest])
 * where the read thread races the poll.
 *
 * Covers what the projector alone decides: text and line separators, soft wrap, the absolute logical index and
 * its advance, the cursor, styles and colors, OSC 8 links, the state snapshot, and the reset that ends exact
 * tracking.
 */
internal class TerminalEmulatorOutputProjectorTest {

  // ---------------------------------------------------------------------------
  // Text and line separators
  // ---------------------------------------------------------------------------

  @Test
  fun `plain text is one logical line at index 0`() = withProjector {
    write("hello")

    val event = collectUpdate()
    assertThat(event.text).isEqualTo("hello")
    assertThat(event.startLineLogicalIndex).isEqualTo(0L)
    assertThat(event.cursorLogicalLineIndex).isEqualTo(0L)
    assertThat(event.cursorColumnIndex).isEqualTo(5)
  }

  @Test
  fun `a hard line end separates logical lines`() = withProjector {
    write("first\r\nsecond")

    val event = collectUpdate()
    assertThat(event.text).isEqualTo("first\nsecond")
    assertThat(event.cursorLogicalLineIndex).isEqualTo(1L)
    assertThat(event.cursorColumnIndex).isEqualTo(6)
  }

  @Test
  fun `trailing blank rows are trimmed`() = withProjector(rows = 24) {
    write("only")

    // The other 23 screen rows are blank and must not reach the event text.
    assertThat(collectUpdate().text).isEqualTo("only")
  }

  @Test
  fun `an untouched screen reports empty text`() = withProjector {
    assertThat(collectUpdate().text).isEmpty()
  }

  // ---------------------------------------------------------------------------
  // Soft wrap
  // ---------------------------------------------------------------------------

  @Test
  fun `a soft-wrapped line stays a single logical line`() = withProjector(columns = 80) {
    val wide = "W".repeat(200)
    write(wide)

    val event = collectUpdate()
    assertThat(event.text).isEqualTo(wide) // no '\n' at the 80-column row boundaries
    assertThat(event.cursorLogicalLineIndex).isEqualTo(0L)
    assertThat(event.cursorColumnIndex).isEqualTo(200)
  }

  @Test
  fun `a wrapped line straddling the finalize boundary is re-emitted whole`() = withProjector(columns = 80, rows = 3) {
    // One unbroken line that keeps scrolling its own rows into history, so a later boundary lands mid-line,
    // and the projector has to back up to the line's first row instead of reporting only its tail.
    write("A".repeat(240)) // exactly fills the 3-row screen
    collectUpdate()
    write("A".repeat(80))
    collectUpdate()
    write("A".repeat(80))

    val event = collectUpdate()
    assertThat(event.startLineLogicalIndex).isEqualTo(0L)
    assertThat(event.text).isEqualTo("A".repeat(400))
  }

  // ---------------------------------------------------------------------------
  // The absolute logical index
  // ---------------------------------------------------------------------------

  @Test
  fun `a later update reports only the newly finalized tail`() = withProjector {
    write((0 until 40).joinToString("\r\n") { "L%02d".format(it) })
    val first = collectUpdate()
    assertThat(first.startLineLogicalIndex).isEqualTo(0L)
    assertThat(first.text).contains("L00", "L39")

    write("\r\n" + (40 until 45).joinToString("\r\n") { "L%02d".format(it) })
    val second = collectUpdate()
    assertThat(second.startLineLogicalIndex).isGreaterThan(0L)
    assertThat(second.text).doesNotContain("L00")
    assertThat(second.cursorLogicalLineIndex).isEqualTo(44L)
  }

  @Test
  fun `the index advances by the logical lines finalized before this update`() = withProjector(rows = 3) {
    // A 3-row screen: every further line finalizes exactly one logical line. The index lags one update
    // behind, because the line finalized by this write is still re-reported by this update.
    write("a\r\nb\r\nc")
    assertThat(collectUpdate().startLineLogicalIndex).isEqualTo(0L)

    write("\r\nd") // finalizes "a", but this update still re-reports it
    assertThat(collectUpdate().startLineLogicalIndex).isEqualTo(0L)

    write("\r\ne")
    assertThat(collectUpdate().startLineLogicalIndex).isEqualTo(1L)

    write("\r\nf")
    assertThat(collectUpdate().startLineLogicalIndex).isEqualTo(2L)
  }

  @Test
  fun `an update with no new output repeats the screen at the same index`() = withProjector {
    write("stable")
    val first = collectUpdate()

    val second = collectUpdate()
    assertThat(second.startLineLogicalIndex).isEqualTo(first.startLineLogicalIndex)
    assertThat(second.text).isEqualTo(first.text)
  }

  @Test
  fun `a burst past the retained scrollback resets the index to 0`() = withProjector(maxScrollbackBytes = 1) {
    // maxScrollbackBytes is floored to two pages, so this burst finalizes far more than is retained.
    write((0 until 40).joinToString("\r\n") { "early-$it" })
    collectUpdate()
    write("\r\n" + (0 until 40).joinToString("\r\n") { "mid-$it" })
    assertThat(collectUpdate().startLineLogicalIndex).isGreaterThan(0L)

    write("\r\n" + (0 until 5_000).joinToString("\r\n") { "burst-$it" })
    val reset = collectUpdate()
    assertThat(reset.startLineLogicalIndex).isEqualTo(0L)
    assertThat(reset.text).contains("burst-4999")
  }

  // ---------------------------------------------------------------------------
  // Coalescing while the output is too fast to track
  // ---------------------------------------------------------------------------

  @Test
  fun `a big window inside a scrollback with room to spare still reports only the screen`() =
    withProjector(rows = 5, maxScrollbackBytes = 8 * 1024 * 1024) {
      // The scrollback holds far more than these rows, so the count stays trackable - the row count alone is
      // what makes reading it not worth the cost. This is the common case early in a session, and the reason
      // the threshold cannot be expressed against the retained scrollback.
      write((0 until 2_000).joinToString("\r\n") { "line-$it" })

      val event = collectUpdate()
      assertThat(event.startLineLogicalIndex).isEqualTo(0L)
      assertThat(event.text.split('\n'))
        .describedAs("only the screen, even though the history was still trackable")
        .hasSizeLessThanOrEqualTo(5)
      assertThat(event.text).contains("line-1999")
    }

  @Test
  fun `a window below the threshold is still reported in full`() =
    withProjector(rows = 5, maxScrollbackBytes = 8 * 1024 * 1024) {
      write((0 until 500).joinToString("\r\n") { "line-$it" })

      val event = collectUpdate()
      assertThat(event.startLineLogicalIndex).isEqualTo(0L)
      assertThat(event.text.split('\n')).hasSize(500)
      assertThat(event.text).contains("line-0", "line-499")
    }

  @Test
  fun `a window that outruns a small scrollback reports only the screen even below the threshold`() =
    withProjector(columns = 215, rows = 5, maxScrollbackBytes = 1) {
      // 215 columns makes a row expensive, so the two-page floor retains only a few hundred rows: these
      // finalized rows outrun it while staying under the row-count threshold.
      write((0 until 600).joinToString("\r\n") { "line-$it" })

      val event = collectUpdate()
      assertThat(event.startLineLogicalIndex).isEqualTo(0L)
      assertThat(event.text.split('\n')).hasSizeLessThanOrEqualTo(5)
    }

  @Test
  fun `a burst past the retained scrollback reports only the screen`() = withProjector(rows = 5, maxScrollbackBytes = 1) {
    write((0 until 5_000).joinToString("\r\n") { "burst-$it" })

    val event = collectUpdate()
    assertThat(event.startLineLogicalIndex).isEqualTo(0L)
    assertThat(event.text.split('\n'))
      .describedAs("only the screen, not the whole retained scrollback")
      .hasSizeLessThanOrEqualTo(5)
    assertThat(event.text).contains("burst-4999")
  }

  @Test
  fun `a wrapped line straddling the screen top is not truncated while only the screen is reported`() =
    withProjector(columns = 80, rows = 5, maxScrollbackBytes = 1) {
      // The burst ends with a 480-character line: six 80-column rows, so its first row is pushed into
      // history while the remaining five fill the screen.
      val wrapped = "A".repeat(480)
      write((0 until 5_000).joinToString("\r\n") { "burst-$it" } + "\r\n" + wrapped)

      val event = collectUpdate()
      assertThat(event.startLineLogicalIndex).isEqualTo(0L)
      assertThat(event.text)
        .describedAs("the line's first row is backed up out of history, so the line stays whole")
        .contains(wrapped)
    }

  @Test
  fun `further heavy output keeps reporting only the screen`() = withProjector(rows = 5, maxScrollbackBytes = 1) {
    write((0 until 5_000).joinToString("\r\n") { "b1-$it" })
    collectUpdate() // starts reporting the screen alone

    write("\r\n" + (0 until 500).joinToString("\r\n") { "b2-$it" }) // still far above the settle threshold
    val event = collectUpdate()
    assertThat(event.startLineLogicalIndex).isEqualTo(0L)
    assertThat(event.text.split('\n')).hasSizeLessThanOrEqualTo(5)
    assertThat(event.text).contains("b2-499")
  }

  @Test
  fun `a single finalized row keeps reporting only the screen`() = withProjector(rows = 5, maxScrollbackBytes = 1) {
    write((0 until 5_000).joinToString("\r\n") { "burst-$it" })
    collectUpdate()

    // One row is enough to count as "still running": the expensive read stays out of the burst.
    write("\r\ntrickle")
    val event = collectUpdate()
    assertThat(event.startLineLogicalIndex).isEqualTo(0L)
    assertThat(event.text.split('\n')).hasSizeLessThanOrEqualTo(5)
  }

  @Test
  fun `once the output stops the retained history is reported again`() = withProjector(rows = 5, maxScrollbackBytes = 1) {
    write((0 until 5_000).joinToString("\r\n") { "burst-$it" })
    collectUpdate()

    // Nothing was written since, so this update finalizes nothing and reads the history back.
    val event = collectUpdate()
    assertThat(event.startLineLogicalIndex).isEqualTo(0L)
    assertThat(event.text.split('\n'))
      .describedAs("the whole retained scrollback, not just the screen")
      .hasSizeGreaterThan(100)
  }

  @Test
  fun `exact tracking resumes after the output stops`() = withProjector(rows = 5, maxScrollbackBytes = 1) {
    write((0 until 5_000).joinToString("\r\n") { "burst-$it" })
    collectUpdate()
    collectUpdate() // stops, so this one re-reads the history

    write("\r\nnext")
    val event = collectUpdate()
    assertThat(event.startLineLogicalIndex)
      .describedAs("the index must grow again, not stay pinned at 0")
      .isGreaterThan(0L)
    assertThat(event.text).contains("next")
  }

  @Test
  fun `steady output keeps reporting only the screen until it pauses`() = withProjector(rows = 5, maxScrollbackBytes = 1) {
    write((0 until 5_000).joinToString("\r\n") { "burst-$it" })
    collectUpdate()

    // A trickle that never stops never settles - the documented cost of waiting for a full stop.
    repeat(5) {
      write("\r\nsteady-$it")
      assertThat(collectUpdate().text.split('\n')).hasSizeLessThanOrEqualTo(5)
    }

    // Only once it pauses does the history come back.
    assertThat(collectUpdate().text.split('\n')).hasSizeGreaterThan(100)
  }

  @Test
  fun `computeCursor stays consistent while only the screen is reported`() = withProjector(rows = 5, maxScrollbackBytes = 1) {
    write((0 until 5_000).joinToString("\r\n") { "burst-$it" })
    val event = collectUpdate()

    val (line, column) = projector.computeCursor()
    assertThat(line).isEqualTo(event.cursorLogicalLineIndex)
    assertThat(column).isEqualTo(event.cursorColumnIndex)
  }

  // ---------------------------------------------------------------------------
  // Cursor
  // ---------------------------------------------------------------------------

  @Test
  fun `computeCursor matches the cursor of the last content update`() = withProjector {
    write((0 until 40).joinToString("\r\n") { "L%02d".format(it) })
    val event = collectUpdate()

    val (line, column) = projector.computeCursor()
    assertThat(line).isEqualTo(event.cursorLogicalLineIndex)
    assertThat(column).isEqualTo(event.cursorColumnIndex)
  }

  @Test
  fun `computeCursor accumulates the column across a wrapped run`() = withProjector(columns = 80) {
    write("W".repeat(200))
    collectUpdate()

    val (line, column) = projector.computeCursor()
    assertThat(line).isEqualTo(0L)
    assertThat(column).isEqualTo(200)
  }

  // ---------------------------------------------------------------------------
  // Styles and colors
  // ---------------------------------------------------------------------------

  @Test
  fun `an ANSI colored run is reported as a style range carrying the color index`() = withProjector {
    write(csi("31m") + "RED" + csi("0m") + "plain")

    val event = collectUpdate()
    assertThat(event.text).isEqualTo("REDplain")
    val red = event.styles.first { it.startOffset == 0L }
    assertThat(red.endOffset).isEqualTo(3L)
    assertThat(red.style.foreground?.colorIndex).isEqualTo(1) // SGR 31 = ANSI index 1
  }

  @Test
  fun `adjacent identical styles merge into one range`() = withProjector {
    write(csi("32m") + "AAA" + "BBB" + csi("0m") + "END")

    val green = collectUpdate().styles.filter { it.style.foreground?.colorIndex == 2 }
    assertThat(green).hasSize(1)
    assertThat(green.single().startOffset).isEqualTo(0L)
    assertThat(green.single().endOffset).isEqualTo(6L)
  }

  @Test
  fun `a style run continues across a soft wrap`() = withProjector(columns = 80) {
    write(csi("34m") + "B".repeat(100) + csi("0m"))

    val blue = collectUpdate().styles.filter { it.style.foreground?.colorIndex == 4 }
    assertThat(blue).hasSize(1)
    assertThat(blue.single().startOffset).isEqualTo(0L)
    assertThat(blue.single().endOffset).isEqualTo(100L)
  }

  @Test
  fun `an extended 256-color index is resolved against the palette`() = withProjector {
    write(csi("38;5;196m") + "X" + csi("0m"))

    val foreground = collectUpdate().styles.first { it.startOffset == 0L }.style.foreground
    assertThat(foreground?.colorIndex).describedAs("must be resolved, not shipped as an index").isNull()
    assertThat(foreground?.rgb).isNotNull()
  }

  @Test
  fun `a truecolor value is reported as rgb`() = withProjector {
    write(csi("38;2;10;20;30m") + "X" + csi("0m"))

    val foreground = collectUpdate().styles.first { it.startOffset == 0L }.style.foreground
    assertThat(foreground?.colorIndex).isNull()
    assertThat(foreground?.rgb).isEqualTo((10 shl 16) or (20 shl 8) or 30)
  }

  @Test
  fun `text attributes map to style options`() = withProjector {
    write(csi("1m") + csi("3m") + csi("4m") + csi("7m") + "X" + csi("0m"))

    assertThat(collectUpdate().styles.first { it.startOffset == 0L }.style.options).contains(
      TextStyleOptionDto.BOLD,
      TextStyleOptionDto.ITALIC,
      TextStyleOptionDto.UNDERLINED,
      TextStyleOptionDto.INVERSE,
    )
  }

  // ---------------------------------------------------------------------------
  // OSC 8 hyperlinks
  // ---------------------------------------------------------------------------

  @Test
  fun `an OSC8 hyperlink is reported as a link range`() = withProjector {
    val uri = "https://example.com/x"
    write("pre " + osc8(uri, "LINK") + " post")

    val event = collectUpdate()
    val start = event.text.indexOf("LINK").toLong()
    assertThat(event.osc8Hyperlinks).containsExactly(Osc8HyperlinkDto(start, start + "LINK".length, uri))
  }

  @Test
  fun `a link spanning a soft wrap stays one range`() = withProjector(columns = 80) {
    val uri = "https://example.com/wrapped"
    val linkText = "L".repeat(20)
    // 70 plain characters first, so the linked run crosses the 80-column row boundary.
    write("X".repeat(70) + osc8(uri, linkText) + "END")

    val event = collectUpdate()
    val start = event.text.indexOf(linkText).toLong()
    assertThat(event.osc8Hyperlinks).containsExactly(Osc8HyperlinkDto(start, start + linkText.length, uri))
  }

  @Test
  fun `unlinked text reports no hyperlinks`() = withProjector {
    write("plain text")

    assertThat(collectUpdate().osc8Hyperlinks).isEmpty()
  }

  // ---------------------------------------------------------------------------
  // The state snapshot
  // ---------------------------------------------------------------------------

  @Test
  fun `buildState reports the cursor, buffer and mouse modes`() = withProjector {
    val initial = getState()
    assertThat(initial.isCursorVisible).isTrue()
    assertThat(initial.isAlternateScreenBuffer).isFalse()
    assertThat(initial.mouseMode).isEqualTo(MouseModeDto.MOUSE_REPORTING_NONE)

    write(csi("?25l"))   // hide the cursor
    write(csi("?1049h")) // alternate screen
    write(csi("?1000h")) // normal mouse tracking
    write(csi("?1006h")) // SGR mouse encoding

    val changed = getState(isShellIntegrationEnabled = true, currentDirectory = "/tmp")
    assertThat(changed.isCursorVisible).isFalse()
    assertThat(changed.isAlternateScreenBuffer).isTrue()
    assertThat(changed.mouseMode).isEqualTo(MouseModeDto.MOUSE_REPORTING_NORMAL)
    assertThat(changed.mouseFormat).isEqualTo(MouseFormatDto.MOUSE_FORMAT_SGR)
    assertThat(changed.isShellIntegrationEnabled).isTrue()
    assertThat(changed.currentDirectory).isEqualTo("/tmp")
  }

  @Test
  fun `buildState folds the cursor shape and the blink flag together`() = withProjector {
    write(csi("4 q")) // DECSCUSR 4 = steady underline
    assertThat(getState().cursorShape).isEqualTo(CursorShapeDto.STEADY_UNDERLINE)

    write(csi("3 q")) // DECSCUSR 3 = blinking underline
    assertThat(getState().cursorShape).isEqualTo(CursorShapeDto.BLINK_UNDERLINE)
  }

  @Test
  fun `buildState reports the window title`() = withProjector {
    write(Char(27) + "]0;My Title" + Char(7))

    assertThat(getState().windowTitle).isEqualTo("My Title")
  }

  // ---------------------------------------------------------------------------
  // Resize
  // ---------------------------------------------------------------------------

  @Test
  fun `a resize that shrinks the screen finalizes rows without a write`() = withProjector(rows = 24) {
    write((0 until 15).joinToString("\r\n") { "R%02d".format(it) })
    collectUpdate()

    emulator.resize(TerminalSize(80, 5))

    assertThat(collectUpdate().text).contains("R14")
  }

  @Test
  fun `a narrowing resize reflows a long line without losing characters`() = withProjector(columns = 80) {
    write("A".repeat(100))
    collectUpdate()

    emulator.resize(TerminalSize(40, 24))

    assertThat(collectUpdate().text.count { it == 'A' }).isEqualTo(100)
  }

  @Test
  fun `a same-column height growth that recovers scrollback keeps exact tracking`() =
    withProjector(columns = 80, rows = 5) {
      // 20 lines into a 5-row screen: 15 scroll off into scrollback.
      write((0 until 20).joinToString("\r\n") { "R%02d".format(it) })
      collectUpdate()

      // Growing to 20 rows recovers all 15 of them back onto the screen (scrollbackRows drops to 0),
      // which must not be mistaken for a burst that overran the retained scrollback.
      emulator.resize(TerminalSize(80, 20))
      val event = collectUpdate()

      assertThat(projector.isHistoryReplaced).isFalse()
      assertThat(event.startLineLogicalIndex).isEqualTo(0L)
      assertThat(event.text.split('\n')).isEqualTo((0 until 20).map { "R%02d".format(it) })
    }

  @Test
  fun `a partial scrollback recovery on growth keeps the earlier anchor`() = withProjector(columns = 80, rows = 5) {
    write((0 until 30).joinToString("\r\n") { "R%02d".format(it) })
    val first = collectUpdate()
    assertThat(first.startLineLogicalIndex).isEqualTo(0L)

    // Growing to 15 rows recovers only some of the 25 scrolled-off lines; whatever remains in scrollback
    // must still be reported as unchanged, not wiped and restarted at index 0.
    emulator.resize(TerminalSize(80, 15))
    val event = collectUpdate()

    assertThat(projector.isHistoryReplaced).isFalse()
    assertThat(event.text.split('\n').last()).isEqualTo("R29")
  }

  @Test
  fun `a growth that recovers exactly one row does not trip the eviction fallback`() =
    withProjector(columns = 80, rows = 5) {
      // 20 lines into a 5-row screen: 15 scroll off into scrollback.
      write((0 until 20).joinToString("\r\n") { "R%02d".format(it) })
      collectUpdate()

      // Growing by a single row recovers exactly one scrolled-off line ("R14") - the smallest possible
      // resize, and the one case a "negative means evicted" heuristic would misclassify as unrecoverable.
      emulator.resize(TerminalSize(80, 6))
      val event = collectUpdate()

      assertThat(projector.isHistoryReplaced).isFalse()
      assertThat(event.startLineLogicalIndex).isEqualTo(14L)
      assertThat(event.text.split('\n')).isEqualTo((14..19).map { "R%02d".format(it) })
    }

  // --- a width change, which reflows -----------------------------------------

  @Test
  fun `a width growth that joins scrollback rows moves the anchor back`() = withProjector(columns = 10, rows = 4) {
    // Each 20-character line takes two rows at 10 columns, so 20 rows exist and 16 scroll off. The
    // screen then starts on line 8.
    val lines = numberedLines(count = 10, length = 20)
    write(lines.joinToString("\r\n"))
    assertThat(collectUpdate().startLineLogicalIndex).isEqualTo(0L)

    // At 40 columns each line fits one row, so 10 rows exist, and the screen starts on line 6.
    emulator.resize(TerminalSize(40, 4))
    val event = collectUpdate()

    assertThat(projector.isHistoryReplaced).isFalse()
    assertThat(event.startLineLogicalIndex).isEqualTo(6L)
    assertThat(event.text.split('\n')).isEqualTo(lines.subList(6, 10))
  }

  @Test
  fun `a width shrink that splits scrollback rows keeps the anchor`() = withProjector(columns = 40, rows = 4) {
    // Each 20-character line fits one row at 40 columns, so 10 rows exist and 6 scroll off.
    val lines = numberedLines(count = 10, length = 20)
    write(lines.joinToString("\r\n"))
    assertThat(collectUpdate().startLineLogicalIndex).isEqualTo(0L)

    // At 10 columns each line takes two rows. The split rows are not new output, so the anchor stays
    // on the line the previous update's screen started on.
    emulator.resize(TerminalSize(10, 4))
    val event = collectUpdate()

    assertThat(projector.isHistoryReplaced).isFalse()
    assertThat(event.startLineLogicalIndex).isEqualTo(6L)
    assertThat(event.text.split('\n')).isEqualTo(lines.subList(6, 10))
  }

  @Ignore(
    "KNOWN GAP: the history mark measures how far the visible screen expanded, so a width " +
    "shrink reports 1200 rows finalized. That is above HISTORY_REPLACE_LINES, so the projector sets " +
    "isHistoryReplaced, anchors at 0 and reports the visible screen alone, which is two logical lines. " +
    "Downstream that deletes every command block."
  )
  @Test
  fun `a width shrink that expands the screen past the replace threshold keeps the history`() =
    withProjector(columns = 200, rows = 50) {
    // Each 200-character line fits one row, so 80 rows exist, 30 scroll off, and the screen starts on
    // line 30.
    val lines = numberedLines(count = 80, length = 200)
    write(lines.joinToString("\r\n"))
    assertThat(collectUpdate().startLineLogicalIndex).isEqualTo(0L)

    // At 8 columns each line takes 25 rows, so the 50 screen rows become 1250. The mark follows the old
    // screen top, so it reports 1200 rows finalized, which is above HISTORY_REPLACE_LINES.
    emulator.resize(TerminalSize(8, 50))
    val event = collectUpdate()

    // A width shrink adds no output, so the anchor must stay on line 30 and the text must still hold
    // lines 30 to 79. A user reaches this by dragging a wide terminal narrow over a long history.
    assertThat(projector.isHistoryReplaced).isFalse()
    assertThat(event.startLineLogicalIndex).isEqualTo(30L)
    assertThat(event.text.split('\n')).isEqualTo(lines.subList(30, 80))
  }

  @Test
  fun `a narrowing to one column loses no character`() = withProjector(columns = 20, rows = 4) {
    write("A".repeat(60))
    collectUpdate()

    emulator.resize(TerminalSize(1, 4))
    val event = collectUpdate()

    assertThat(projector.isHistoryReplaced).isFalse()
    assertThat(event.startLineLogicalIndex).isEqualTo(0L)
    assertThat(event.text).isEqualTo("A".repeat(60))
  }

  @Test
  fun `a reflow that joins rows keeps one style run`() = withProjector(columns = 20, rows = 4) {
    // 30 red characters take two rows at 20 columns and one row at 40.
    write("$ESC[31m" + "R".repeat(30) + "$ESC[0m")
    collectUpdate()

    emulator.resize(TerminalSize(40, 4))
    val event = collectUpdate()

    assertThat(event.text).isEqualTo("R".repeat(30))
    assertThat(event.styles).hasSize(1)
    assertThat(event.styles.single().startOffset).isEqualTo(0L)
    assertThat(event.styles.single().endOffset).isEqualTo(30L)
  }

  // --- both dimensions at once -----------------------------------------------

  @Test
  fun `a growth in both dimensions moves the anchor back`() = withProjector(columns = 10, rows = 4) {
    val lines = numberedLines(count = 10, length = 20)
    write(lines.joinToString("\r\n"))
    collectUpdate()

    // At 40 columns 10 rows exist, and an 8-row screen starts on line 2.
    emulator.resize(TerminalSize(40, 8))
    val event = collectUpdate()

    assertThat(projector.isHistoryReplaced).isFalse()
    assertThat(event.startLineLogicalIndex).isEqualTo(2L)
    assertThat(event.text.split('\n')).isEqualTo(lines.subList(2, 10))
  }

  @Test
  fun `a shrink in both dimensions keeps the anchor`() = withProjector(columns = 10, rows = 4) {
    val lines = numberedLines(count = 10, length = 20)
    write(lines.joinToString("\r\n"))
    collectUpdate()

    // At 5 columns each line takes four rows, so 40 rows exist and a 2-row screen starts inside line 9.
    emulator.resize(TerminalSize(5, 2))
    val event = collectUpdate()

    assertThat(projector.isHistoryReplaced).isFalse()
    assertThat(event.startLineLogicalIndex).isEqualTo(8L)
    assertThat(event.text.split('\n')).isEqualTo(lines.subList(8, 10))
  }

  @Test
  fun `a width growth with a height shrink moves the anchor back`() = withProjector(columns = 10, rows = 4) {
    val lines = numberedLines(count = 10, length = 20)
    write(lines.joinToString("\r\n"))
    collectUpdate()

    // At 40 columns 10 rows exist, and a 3-row screen starts on line 7.
    emulator.resize(TerminalSize(40, 3))
    val event = collectUpdate()

    assertThat(projector.isHistoryReplaced).isFalse()
    assertThat(event.startLineLogicalIndex).isEqualTo(7L)
    assertThat(event.text.split('\n')).isEqualTo(lines.subList(7, 10))
  }

  @Test
  fun `a width shrink with a height growth re-emits the straddled line whole`() = withProjector(columns = 10, rows = 4) {
    val lines = numberedLines(count = 10, length = 20)
    write(lines.joinToString("\r\n"))
    collectUpdate()

    // At 5 columns the 10-row screen starts inside line 8, so the window begins on a wrap continuation.
    // The projector must back up to that line's first row and report line 8 whole, not only its tail.
    emulator.resize(TerminalSize(5, 10))
    val event = collectUpdate()

    assertThat(projector.isHistoryReplaced).isFalse()
    assertThat(event.startLineLogicalIndex).isEqualTo(8L)
    assertThat(event.text.split('\n')).isEqualTo(lines.subList(8, 10))
  }

  @Test
  fun `a height shrink onto a wrapped row re-emits that line whole`() = withProjector(columns = 10, rows = 4) {
    // Each 30-character line takes three rows, so 15 rows exist and the 4-row screen starts on the last
    // row of line 3.
    val lines = numberedLines(count = 5, length = 30)
    write(lines.joinToString("\r\n"))
    collectUpdate()

    emulator.resize(TerminalSize(10, 2))
    val event = collectUpdate()

    assertThat(projector.isHistoryReplaced).isFalse()
    assertThat(event.startLineLogicalIndex).isEqualTo(3L)
    assertThat(event.text.split('\n')).isEqualTo(lines.subList(3, 5))
  }

  // --- the alternate screen --------------------------------------------------

  @Ignore(
    "KNOWN GAP: the history mark still points into the primary screen while scrollbackRows " +
    "already reads the alternate one, so finalizedLineCount reports a recovery of 15 rows that no resize " +
    "caused. The anchor lands on line 10, and the alternate model then pads ten empty lines above the " +
    "content. No resize is needed. A filled primary scrollback is enough."
  )
  @Test
  fun `entering the alternate screen over a filled scrollback keeps the anchor at 0`() =
    withProjector(columns = 80, rows = 5) {
      // 20 lines put 15 into the scrollback, so the primary screen starts on line 15.
      write((0 until 20).joinToString("\r\n") { "R%02d".format(it) })
      assertThat(collectUpdate().startLineLogicalIndex).isEqualTo(0L)

      // The cursor keeps its column across the switch, so "ALT" starts three columns in, under "R19".
      write("$ESC[?1049h" + "ALT")
      val event = collectUpdate()

      // The alternate screen holds no scrollback and its output model starts empty, so this content must
      // be reported at index 0.
      assertThat(projector.isHistoryReplaced).isFalse()
      assertThat(event.startLineLogicalIndex).isEqualTo(0L)
      assertThat(event.text.split('\n')).isEqualTo(listOf("", "", "", "", "   ALT"))
    }

  @Ignore(
    "KNOWN GAP: the anchor holds the value the buffer switch already put there, which is 10. " +
    "The resize itself adds no error. See the case above."
  )
  @Test
  fun `a resize on the alternate screen keeps the anchor at 0`() = withProjector(columns = 80, rows = 5) {
    write((0 until 20).joinToString("\r\n") { "R%02d".format(it) })
    collectUpdate()
    write("$ESC[?1049h" + "ALT")
    collectUpdate()

    emulator.resize(TerminalSize(40, 10))
    val event = collectUpdate()

    // The alternate screen still holds only its own content, so the anchor must be 0.
    assertThat(projector.isHistoryReplaced).isFalse()
    assertThat(event.startLineLogicalIndex).isEqualTo(0L)
    assertThat(event.text.split('\n')).isEqualTo(listOf("", "", "", "", "   ALT"))
  }

  @Ignore(
    "KNOWN GAP, the severe half: the projector reports the anchor 10 with the text of all 20 " +
    "lines. The model then writes \"R00\" at logical line 10, which duplicates the whole history."
  )
  @Test
  fun `leaving the alternate screen after a resize restores the primary anchor`() =
    withProjector(columns = 80, rows = 5) {
      write((0 until 20).joinToString("\r\n") { "R%02d".format(it) })
      collectUpdate()
      write("$ESC[?1049h" + "ALT")
      collectUpdate()
      emulator.resize(TerminalSize(80, 10))
      collectUpdate()

      write("$ESC[?1049l")
      val event = collectUpdate()

      // On the primary screen the 20 lines now fit a 10-row screen with 10 in the scrollback, so the
      // anchor must be 10 and the text must hold lines 10 to 19.
      assertThat(projector.isHistoryReplaced).isFalse()
      assertThat(event.startLineLogicalIndex).isEqualTo(10L)
      assertThat(event.text.split('\n')).isEqualTo((10 until 20).map { "R%02d".format(it) })
    }

  @Ignore(
    "KNOWN GAP: the buffer holds 20 lines, numbered 0 to 19, so no anchor above 10 is " +
    "reachable. The projector reports 20, which is past the end of its own content. The drift stays for " +
    "the rest of the session, because nothing re-anchors screenTopLogical."
  )
  @Test
  fun `the anchor stays inside the buffer after the alternate screen is left`() =
    withProjector(columns = 80, rows = 5) {
      write((0 until 20).joinToString("\r\n") { "R%02d".format(it) })
      collectUpdate()
      write("$ESC[?1049h" + "ALT")
      collectUpdate()
      emulator.resize(TerminalSize(80, 10))
      collectUpdate()
      write("$ESC[?1049l")
      collectUpdate()

      val event = collectUpdate()

      // The buffer holds 20 lines, numbered 0 to 19, so the screen top stays on line 10. A later update
      // with no new output must repeat the screen at that same anchor.
      assertThat(event.startLineLogicalIndex).isEqualTo(10L)
      assertThat(event.text.split('\n')).isEqualTo((10 until 20).map { "R%02d".format(it) })
    }

  @Ignore(
    "KNOWN GAP, the root cause: finalizedLineCount reports a recovery of 36 rows, because it " +
    "reads the alternate screen's empty scrollback against a mark still pinned in the primary screen. " +
    "The projector clamps that to the 24 screen rows, counts each empty alternate row as one logical " +
    "line, and subtracts 24 from a screen top of 12. The anchor becomes -12. " +
    "MutableTerminalOutputModelImpl.updateContent passes it to getStartOfLine, which throws " +
    "IndexOutOfBoundsException. That error stops the frontend output-flow collection, so the terminal " +
    "stops updating."
  )
  @Test
  fun `the anchor never goes negative on the alternate screen`() = withProjector(columns = 80, rows = 24) {
    // 20 lines of 200 characters take three rows each at 80 columns, so 60 rows exist. The 24-row screen
    // leaves 36 rows in the scrollback, but those hold only 12 logical lines.
    write(numberedLines(count = 20, length = 200).joinToString("\r\n"))
    assertThat(collectUpdate().startLineLogicalIndex).isEqualTo(0L)

    write("$ESC[?1049h")
    val event = collectUpdate()

    // An anchor addresses a logical line of the output, so it can never be negative. The alternate
    // screen starts empty, so this one must be 0.
    assertThat(event.startLineLogicalIndex).isEqualTo(0L)
  }

  // --- a resize beside the other projector states ----------------------------

  @Test
  fun `a resize while the history is replaced resumes exact tracking`() =
    withProjector(columns = 80, rows = 5, maxScrollbackBytes = 1) {
      // maxScrollbackBytes is floored to two pages, so this burst finalizes far more than is retained.
      write((0 until 5_000).joinToString("\r\n") { "R%04d".format(it) })
      collectUpdate()
      assertThat(projector.isHistoryReplaced).isTrue()

      // The lines are five characters wide, so this resize reflows nothing and finalizes no row. The
      // next update therefore ends the replacement and reads the retained window again.
      emulator.resize(TerminalSize(40, 5))
      val event = collectUpdate()

      assertThat(projector.isHistoryReplaced).isFalse()
      assertThat(event.startLineLogicalIndex).isEqualTo(0L)
      assertThat(event.text).contains("R4999")
    }

  @Test
  fun `several resizes between two updates report the final geometry`() = withProjector(columns = 10, rows = 4) {
    val lines = numberedLines(count = 10, length = 20)
    write(lines.joinToString("\r\n"))
    collectUpdate()

    emulator.resize(TerminalSize(20, 6))
    emulator.resize(TerminalSize(40, 8))
    emulator.resize(TerminalSize(40, 4))
    val event = collectUpdate()

    assertThat(projector.isHistoryReplaced).isFalse()
    assertThat(event.startLineLogicalIndex).isEqualTo(6L)
    assertThat(event.text.split('\n')).isEqualTo(lines.subList(6, 10))
  }

  @Test
  fun `a resize to the same size reports the screen at the same anchor`() = withProjector(columns = 10, rows = 4) {
    val lines = numberedLines(count = 10, length = 20)
    write(lines.joinToString("\r\n"))
    collectUpdate()

    emulator.resize(TerminalSize(10, 4))
    val event = collectUpdate()

    assertThat(projector.isHistoryReplaced).isFalse()
    assertThat(event.startLineLogicalIndex).isEqualTo(8L)
    assertThat(event.text.split('\n')).isEqualTo(lines.subList(8, 10))
  }

  @Test
  fun `the cursor after a resize agrees between the update and computeCursor`() =
    withProjector(columns = 10, rows = 4) {
      val lines = numberedLines(count = 10, length = 20)
      write(lines.joinToString("\r\n"))
      collectUpdate()

      // Line 9 fills its last row exactly, so the cursor rests on that row's final cell with the wrap
      // pending. Its column is therefore 19, one before the 20-character line end.
      emulator.resize(TerminalSize(40, 4))
      val event = collectUpdate()

      assertThat(event.cursorLogicalLineIndex).isEqualTo(9L)
      assertThat(event.cursorColumnIndex).isEqualTo(19)
      assertThat(projector.computeCursor()).isEqualTo(9L to 19)
    }

  // ---------------------------------------------------------------------------
  // Two updates from one write
  // ---------------------------------------------------------------------------

  @Test
  fun `a custom command inside one write yields two composable updates`() = withProjector(rows = 3) {
    val insideTheListener = ArrayList<TerminalContentUpdatedEvent>()
    emulator.customCommandListener = TerminalCustomCommandListener { insideTheListener.add(collectUpdate()) }

    write("a\r\nb\r\nc")
    assertThat(collectUpdate().startLineLogicalIndex).isEqualTo(0L)

    write("\r\nd" + promptStartedOsc() + "\r\ne")

    val atTheCommand = insideTheListener.single()
    assertThat(atTheCommand.startLineLogicalIndex).isEqualTo(0L)
    assertThat(atTheCommand.text).isEqualTo("a\nb\nc\nd")

    val afterTheCommand = collectUpdate()
    assertThat(afterTheCommand.startLineLogicalIndex).isEqualTo(1L)
    assertThat(afterTheCommand.text).isEqualTo("b\nc\nd\ne")
  }

  // ---------------------------------------------------------------------------
  // Harness
  // ---------------------------------------------------------------------------

  private class Fixture(val emulator: TerminalEmulator, val projector: TerminalEmulatorOutputProjector) {
    fun write(text: String) = emulator.write(text)

    fun collectUpdate(): TerminalContentUpdatedEvent = projector.buildContentUpdate()

    fun getState(isShellIntegrationEnabled: Boolean = false, currentDirectory: String? = null): TerminalStateDto =
      projector.buildState(isShellIntegrationEnabled, currentDirectory)
  }

  private fun withProjector(
    columns: Int = 80,
    rows: Int = 24,
    maxScrollbackBytes: Int = 1024 * 1024,
    body: Fixture.() -> Unit,
  ) {
    val emulator = createTerminalEmulator(TerminalSize(columns, rows), maxScrollbackBytes)
    val projector = TerminalEmulatorOutputProjector(emulator)
    try {
      Fixture(emulator, projector).body()
    }
    finally {
      runCatching { projector.close() }
      runCatching { emulator.close() }
    }
  }

  /**
   * [count] hard lines of exactly [length] characters. Each one starts with its own index, so a case can
   * name the line an assertion is about, and a fixed length keeps the row count per line exact at a
   * given width.
   */
  private fun numberedLines(count: Int, length: Int): List<String> =
    (0 until count).map { "L$it".padEnd(length, '-') }

  /** `ESC ] 8 ; ; <uri> ST <text> ESC ] 8 ; ; ST` - [text] hyperlinked to [uri]. */
  private fun osc8(uri: String, text: String): String {
    val st = Char(27) + "\\"
    return Char(27) + "]8;;" + uri + st + text + Char(27) + "]8;;" + st
  }
}

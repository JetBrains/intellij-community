// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.terminal.emulator.TerminalEmulator
import com.intellij.terminal.emulator.TerminalSize
import com.intellij.terminal.emulator.createTerminalEmulator
import com.intellij.terminal.frontend.session.ghostty.TerminalEmulatorOutputProjector
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.CursorShapeDto
import org.jetbrains.plugins.terminal.session.impl.dto.MouseFormatDto
import org.jetbrains.plugins.terminal.session.impl.dto.MouseModeDto
import org.jetbrains.plugins.terminal.session.impl.dto.Osc8HyperlinkDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalStateDto
import org.jetbrains.plugins.terminal.session.impl.dto.TextStyleOptionDto
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

  /** `ESC ] 8 ; ; <uri> ST <text> ESC ] 8 ; ; ST` - [text] hyperlinked to [uri]. */
  private fun osc8(uri: String, text: String): String {
    val st = Char(27) + "\\"
    return Char(27) + "]8;;" + uri + st + text + Char(27) + "]8;;" + st
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Exercises [IncrementalTextBuffer]'s full text buffer — the complete finalized history it accumulates from a
 * [TerminalEmulator.markHistoryBoundary] mark, which (unlike the emulator's bounded scrollback) keeps every line
 * that ever scrolled off, even past the eviction cap. This is the "append the scrolled-off lines to a growing
 * document" model an editor-backed renderer uses.
 *
 * (Every other test also validates the mark implicitly: [IncrementalTextBuffer.sync] asserts on each sync that the
 * emulator's retained scrollback equals the tail of this accumulated history.)
 */
class FullTextBufferTest {

  @Test
  fun `full buffer keeps every scrolled-off line past the eviction cap`() = session(4_000, 2) { session ->
    // A very wide terminal makes each scrollback row expensive (storage is charged per grid cell), so the ~1 MiB
    // scrollback floor retains only a couple dozen rows — far fewer than the lines streamed below, guaranteeing
    // the oldest are evicted from the emulator while the full buffer keeps them all.
    val rows = session.emulator.size.rows
    val total = 100

    // Fill the screen (no scroll yet), then stream the rest. Syncing the mirror after every line (via an assert)
    // keeps the mark from ever falling behind, so it counts each finalized line exactly.
    session.write("line0")
    session.crlf().write("line1")
    session.assertScreenLines("line0", "line1")
    for (i in rows until total) {
      session.crlf().write("line$i")
      session.expectFullRebuild() // a scroll repaints the whole screen
      session.assertScreenLines("line${i - 1}", "line$i")
    }

    val finalizedCount = total - rows // the last `rows` lines stay on the active screen

    // The full buffer reconstructed every scrolled-off line, in order...
    assertThat(session.fullScrollbackLines()).isEqualTo((0 until finalizedCount).map { "line$it" })
    // ...and the full buffer + active screen is the entire streamed output.
    assertThat(session.fullBufferLines()).isEqualTo((0 until total).map { "line$it" })

    // ...even though the emulator itself retained only a small window (proving the mark recovered lines a
    // scrollbackRows-delta renderer would have lost to eviction).
    assertThat(session.scrollbackRowCount())
      .describedAs("expected the scrollback cap to have evicted lines")
      .isLessThan(finalizedCount)
  }
}

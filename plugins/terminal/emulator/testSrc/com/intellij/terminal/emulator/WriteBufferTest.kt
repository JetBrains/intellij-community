// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [TerminalEmulator.write] copies its input into one fixed-size native buffer and streams anything longer
 * through it a chunk at a time.
 *
 * That buffer is deliberately never resized: the engine's memory comes from a shared `Arena` that frees
 * nothing before `close()`, so both allocating per write and growing on demand would strand native memory
 * for the emulator's whole life. Chunking is safe because `ghostty_terminal_vt_write` drives a stream parser
 * that carries state across calls — but it does mean the split must be invisible, which is what these tests
 * pin down.
 */
class WriteBufferTest {

  @Test
  fun `a short write after a long one does not replay the leftover bytes`() = session(40, 5) { session ->
    session.write("0123456789ABCDEFGHIJ")
    session.crlf()
    session.write("hi")

    session.assertScreenLines("0123456789ABCDEFGHIJ", "hi")
  }

  @Test
  fun `a write spanning several chunks is neither truncated nor corrupted`() = session(40, 5) { session ->
    // Comfortably more than one buffer's worth: 512 full rows of 'z', then a marker on the row below.
    session.write("z".repeat(20 * 1024))
    session.crlf()
    session.write("after")

    // Scrolling 512 rows repaints the whole screen, so the incremental mirror legitimately rebuilds.
    session.expectFullRebuild()
    session.assertScreenRow(3, "z".repeat(40))
    session.assertScreenRow(4, "after")
  }

  /**
   * The real contract: where the chunk boundaries fall must not be observable. One oversized write (split
   * internally) has to land exactly like the same bytes delivered in pieces small enough never to be split —
   * including escape sequences that straddle a boundary.
   */
  @Test
  fun `chunking a large write matches feeding the same bytes piecemeal`() {
    // ~26 KB, so the single write spans two chunks. Styled so the payload carries escape sequences, which are
    // the interesting thing to cut in half. Scrollback is raised so nothing is evicted and the two runs stay
    // comparable.
    val payload = buildString {
      for (i in 0 until 2_000) {
        append(csi("3${i % 8}m")).append("line").append(i).append(csi("0m")).append("\r\n")
      }
    }

    session(80, 5, maxScrollbackBytes = 8 * 1024 * 1024) { whole ->
      session(80, 5, maxScrollbackBytes = 8 * 1024 * 1024) { piecemeal ->
        whole.write(payload)
        for (piece in payload.chunked(97)) { // 97: a prime, so boundaries drift through the sequences
          piecemeal.write(piece)
        }

        assertThat(whole.snapshot())
          .describedAs("a chunk boundary changed the result")
          .isEqualTo(piecemeal.snapshot())
      }
    }
  }
}

/** Scrollback followed by the active screen, read straight from the emulator (no incremental mirror). */
private fun EmulatorTestSession.snapshot(): List<String> =
  (0 until emulator.scrollbackRows).map { emulator.scrollbackLine(it).toStyledText().text } +
  (0 until emulator.size.rows).map { emulator.screenLine(it).toStyledText().text }

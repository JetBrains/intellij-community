// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.junit.jupiter.api.Test

/**
 * The scrollback buffer: writing past the bottom row moves the oldest rows into scrollback, a resize
 * reflows across the screen/scrollback boundary, and the retained history is bounded by
 * the configured `maxScrollbackBytes` (see [createTerminalEmulator]).
 */
class ScrollbackBufferTest {

  @Test
  fun scrollOnNewLine() = session(5, 3) { session ->
    session.writeLinesWithCrlf((1..4).map { "line$it" })

    session.expectFullRebuild()
    session.assertScrollbackLines("line1")
    session.assertScreenLines("line2", "line3", "line4")
    session.assertCursorPosition(5, 3)
  }

  @Test
  fun scrollOnWrapping() = session(5, 3) { session ->
    session.write("line1").crlf()
    session.write("line2").crlf()
    session.write("line3").crlf()
    session.write("line4")
    session.write("4")
    session.write("4")

    session.expectFullRebuild()
    session.assertScrollbackLines("line1", "line2")
    session.assertScreenLines("line3", "line4", "44")
    session.assertCursorPosition(3, 3)
  }

  @Test
  fun scrollAndResize() = session(10, 4) { session ->
    session.write("1234567890").crlf()
    session.write("2345678901").crlf()

    session.assertCursorPosition(1, 3)

    session.resize(7, 4)

    session.assertScrollbackLines("1234567")
    session.assertScreenLines("890", "2345678", "901")

    session.write("3456789").crlf()

    session.expectFullRebuild()
    session.assertScrollbackLines("1234567", "890")
    session.assertScreenLines("2345678", "901", "3456789")

    session.assertCursorPosition(1, 4)
  }

  @Test
  fun `scrollback is off with maxScrollbackBytes=0`() = session(10, 2, maxScrollbackBytes = 0) { session ->
    // maxScrollbackBytes = 0 disables scrollback: every line that scrolls off
    // the screen buffer is dropped immediately
    session.writeLinesWithCrlf((1..8).map { "line_$it" })
    session.assertScreenLines("line_7", "line_8")
    session.assertScrollbackLines()
  }
}

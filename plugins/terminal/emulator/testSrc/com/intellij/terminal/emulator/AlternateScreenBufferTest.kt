// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.junit.jupiter.api.Test

/**
 * The alternate screen buffer (DEC 1049, plus the legacy `?47` variant) that full-screen programs use
 * — editors, pagers and TUIs such as `vim`, `less`, `htop`, Claude Code. Entering it must not disturb
 * the primary screen or its scrollback, and exiting must restore them; these tests exercise realistic
 * enter → draw → exit round trips.
 */
class AlternateScreenBufferTest {

  @Test
  fun altScreenStartsBlankAndRestoresPrimaryOnExit() = session(5, 3) { session ->
    session.write("1.").crlf().write("2.").crlf()

    session.useAlternateBuffer(true)
    session.expectFullRebuild()
    session.assertScreenLines()            // the alternate screen starts cleared

    session.write("xxxxx").crlf().write("yyyyy").crlf()

    session.useAlternateBuffer(false)
    session.expectFullRebuild()
    session.assertScreenLines("1.", "2.")  // the primary screen is restored untouched
  }

  @Test
  fun altScreenActivityDoesNotAffectPrimaryScrollback() = session(200, 2, maxScrollbackBytes = 2_000_000) { session ->
    // A modest 2 MB limit holds only a few hundred rows at this width. Build a small, known primary
    // scrollback well inside it.
    session.write("p1").crlf().write("p2").crlf().write("p3").crlf().write("p4")
    session.expectFullRebuild()
    session.assertScreenLines("p3", "p4")
    session.assertScrollbackLines("p1", "p2")

    // A full-screen program takes the alt screen and floods it with far more output than the whole
    // scrollback limit could hold (3000 rows >> the ~few-hundred-row limit). The alt screen has no
    // scrollback of its own, so this output is scrolled away, never accumulated.
    session.useAlternateBuffer(true)
    repeat(3_000) { i ->
      if (i > 0) session.crlf()
      session.write("noise")
    }
    session.useAlternateBuffer(false)

    // The primary screen and its scrollback are unchanged: the flood neither evicted primary history
    // nor counted against the primary's scrollback limit.
    session.expectFullRebuild()
    session.assertScreenLines("p3", "p4")
    session.assertScrollbackLines("p1", "p2")
  }

  @Test
  fun restoresCursorPositionOnExit() = session(10, 4) { session ->
    session.write("prompt>")           // 7 chars -> cursor at column 8, row 1
    session.assertCursorPosition(8, 1)

    session.useAlternateBuffer(true)   // DEC 1049 saves the cursor
    session.cursorPosition(5, 3)       // the program moves the cursor around the alt screen
    session.write("UI")

    session.useAlternateBuffer(false)  // DEC 1049 restores the saved cursor
    session.assertCursorPosition(8, 1)
    session.expectFullRebuild()
    session.assertScreenLines("prompt>")
  }

  @Test
  fun fullScreenTuiRoundTrip() = session(12, 4) { session ->
    // A shell session whose history has already scrolled off the top (6 lines on a 4-row screen).
    session
      .write("$ ls").crlf()
      .write("a.txt b.txt").crlf()
      .write("$ pwd").crlf()
      .write("/home/me").crlf()
      .write("$ claude").crlf()
      .write("resuming")
    session.expectFullRebuild()
    session.assertScrollbackLines("$ ls", "a.txt b.txt")
    session.assertScreenLines("$ pwd", "/home/me", "$ claude", "resuming")

    // Claude Code launches: it takes the alternate screen (1049 saves the cursor + clears), then
    // paints a full-screen UI with absolute cursor moves.
    session.useAlternateBuffer(true)
    session.expectFullRebuild()
    session.assertScreenLines()                       // alt screen starts cleared
    session.cursorPosition(1, 1)
    session.write("Claude Code")
    session.cursorPosition(1, 4)
    session.write(">")
    session.assertScreenLines("Claude Code", "", "", ">")

    // Quitting restores the shell exactly: primary screen and scrollback both intact.
    session.useAlternateBuffer(false)
    session.expectFullRebuild()
    session.assertScreenLines("$ pwd", "/home/me", "$ claude", "resuming")
    session.assertScrollbackLines("$ ls", "a.txt b.txt")
  }

  @Test
  fun legacyAlternateScreenSequenceRoundTrips() = session(6, 3) { session ->
    // Older full-screen programs use the legacy `?47` pair instead of `?1049`.
    session.write("main")
    session.assertScreenLines("main")

    session.write(csi("?47h")) // switch to the alternate screen
    session.write("overlay")

    session.write(csi("?47l")) // switch back to the primary screen
    session.expectFullRebuild()
    session.assertScreenLines("main")
  }
}

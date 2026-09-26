// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.junit.jupiter.api.Test

/**
 * Sequences that blank cells in place: RIS (`ESC c`), Erase in Display (`CSI J`), erase saved lines
 * (DECSED `CSI 3 J`), Erase Character (`CSI X`) and the selective erases that spare cells marked
 * protected with DECSCA (`CSI Ps " q`). Each writes some content, erases a region, and asserts what
 * remains on the screen / in scrollback. Insert/delete editing that *shifts* content lives in
 * [TextBufferTest].
 *
 * The selective-erase scenarios are ported from libvterm's `t/65screen_protect.test` (MIT, © Paul Evans).
 */
class EraseTest {

  @Test
  fun resetToInitialState() = session(20, 4) { session ->
    repeat(9) { i ->
      if (i > 0) session.crlf()
      session.write("foo ${i + 1}")
    }
    session.expectFullRebuild()
    session.assertScreenLines("foo 6", "foo 7", "foo 8", "foo 9")
    session.assertScrollbackLines("foo 1", "foo 2", "foo 3", "foo 4", "foo 5")

    session.write(esc("c")) // RIS

    session.expectFullRebuild()
    session.assertScreenLines()
    session.assertScrollbackLines()
  }

  @Test
  fun eraseSavedLines() = session(20, 2) { session ->
    repeat(5) { i ->
      if (i > 0) session.crlf()
      session.write("foo ${i + 1}")
    }
    session.expectFullRebuild()
    session.assertScreenLines("foo 4", "foo 5")
    session.assertScrollbackLines("foo 1", "foo 2", "foo 3")

    session.write(csi("3J")) // erase saved lines (scrollback)

    session.expectFullRebuild()
    session.assertScreenLines("foo 4", "foo 5")
    session.assertScrollbackLines()
  }

  @Test
  fun eraseInDisplayToEnd() = session(10, 5) { session ->
    session.write(csi("5;1H"))          // cursor to the bottom row
    session.write("foo\r\nbar\r\nbaz")  // scrolls; foo/bar/baz end up at the bottom
    session.write(csi("A"))             // cursor up one row (onto "bar")
    session.write("\r")                 // to column 0
    session.write(csi("0J"))            // erase from the cursor to the end of the screen

    session.expectFullRebuild()
    session.assertScreenLines("", "", "foo")
  }

  /**
   * A selective erase (DECSED, `CSI ? J`) spares cells written while DECSCA protection was on:
   * `A` and `C` are erased, the protected `B` survives.
   */
  @Test
  fun selectiveEraseSparesProtectedCells() = session(20, 2) { session ->
    session.write("A" + csi("1\"q") + "B" + csi("\"q") + "C") // DECSCA 1 protects B; DECSCA (default 0) unprotects
    session.assertScreenLines("ABC")

    session.write(csi("G") + csi("?J")) // to column 1, then selective erase to the end of the display
    session.assertScreenLines(" B")
  }

  /** A plain erase (ED, `CSI J`) ignores DECSCA protection and blanks everything. */
  @Test
  fun plainEraseIgnoresProtectedCells() = session(20, 2) { session ->
    session.write("A" + csi("1\"q") + "B" + csi("\"q") + "C")
    session.assertScreenLines("ABC")

    session.write(csi("G") + csi("J"))
    session.assertScreenLines()
  }

  @Test
  fun eraseCharacters() = session(5, 2) { session ->
    session.write("11111")

    session.cursorPosition(2, 1)

    session.eraseCharacters(2)

    session.assertScreenLines("1  11")

    session.eraseCharacters(10)

    session.assertScreenLines("1")
  }
}

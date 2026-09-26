// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * What lands in the text buffer (the grid): plain printing, and the insert/delete editing functions
 * that *shift* existing content — insert/delete line (IL/DL) and insert/delete character (ICH/DCH).
 * Each feeds raw VT bytes and reads the resulting screen back through the [TerminalEmulator] API.
 * Blanking-in-place erase functions live in [EraseTest]; wide-character handling in [WideCharTest]; the
 * soft-wrap flag the wrapped rows carry in [SoftWrapTest].
 */
class TextBufferTest {

  @Test
  fun rendersTextWithSgrColorAndTracksCursor() = session(20, 5) { session ->
    session.write("Hello " + csi("31m") + "RED" + csi("0m") + "!")

    session.assertScreenLines("Hello RED!")

    val cells = session.screenLine(0).cells
    // 'R' is the 7th cell (index 6); SGR 31 -> palette index 1 (named red).
    assertThat(cells[6].style.foreground).isEqualTo(TerminalColor.IndexedAnsi(1))
    // The leading "Hello " uses the default (unset) foreground.
    assertThat(cells[0].style.foreground).isEqualTo(TerminalColor.Default)

    // Cursor followed the output: 10 glyphs written -> column 10, row 0 (0-based).
    session.assertCursorPosition(11, 1)
  }

  @Test
  fun charactersFromUnsupportedCsiAreNotPrinted() = session(20, 2) { session ->
    // Kitty keyboard-protocol sequences the engine does not implement must be swallowed, not printed.
    session.write("foo" + csi("=5u") + " bar" + csi("=0u") + " baz" + csi("<u"))
    session.assertScreenLines("foo bar baz")
  }

  @Test
  fun longLineSoftWrapsAtWidth() = session(5, 3) { session ->
    // A line longer than the screen width soft-wraps onto the next row(s) rather than being truncated.
    session.write("abcdefghijkl") // 12 chars on a 5-column screen
    session.assertScreenLines("abcde", "fghij", "kl")
    session.assertCursorPosition(3, 3)
  }

  @Test
  fun emptyLineTextStyle() = session(15, 10) { session ->
    session.write("  1. line1")
    session.crlf()
    session.write("  2. line2")
    session.crlf()
    session.crlf()
    session.crlf()
    session.write("  3. line3")
    session.crlf()
    session.crlf()
    session.write("  4.")

    session.assertScreenLines("  1. line1", "  2. line2", "", "", "  3. line3", "", "  4.")
  }

  @Test
  fun insertLine() = session(5, 3) { session ->
    session.write("1")
    session.crlf()
    session.write("2")
    session.crlf()
    session.write("3")

    session.cursorPosition(1, 2)

    session.insertLines(1)

    session.write("3")

    session.assertScreenLines("1", "3", "2")
  }

  @Test
  fun insertLine2() = session(5, 3) { session ->
    session.write("1")
    session.crlf()
    session.write("2")
    session.crlf()
    session.write("3")

    session.cursorPosition(1, 1)

    session.insertLines(2)

    session.write("3")
    session.crlf()

    session.assertScreenLines("3", "", "1")

    session.insertLines(20)

    session.assertScreenLines("3")
  }

  @Test
  fun insertLineScrollingRegion() = session(5, 3) { session ->
    session.write("1")
    session.crlf()
    session.write("2")
    session.crlf()
    session.write("=")

    session.setScrollingRegion(1, 2)

    session.cursorPosition(1, 1)

    session.insertLines(1)

    session.write("3")
    session.crlf()

    session.assertScreenLines("3", "1", "=")
  }

  @Test
  fun insertLineScrollingRegionManyLines() = session(5, 3) { session ->
    session.write("1")
    session.crlf()
    session.write("2")
    session.crlf()
    session.write("=")

    session.setScrollingRegion(1, 2)

    session.cursorPosition(1, 1)

    session.insertLines(20)

    session.write("3")
    session.crlf()

    session.assertScreenLines("3", "", "=")
  }

  @Test
  fun deleteLines() = session(5, 5) { session ->
    session.write("1")
    session.crlf()
    session.write("2")
    session.crlf()
    session.write("3")
    session.crlf()
    session.write("4")
    session.crlf()

    session.setScrollingRegion(1, 3)

    session.cursorPosition(1, 2)

    session.deleteLines(2)

    session.assertScreenLines("1", "", "", "4")
  }

  @Test
  fun deleteManyLines() = session(5, 5) { session ->
    session.write("1")
    session.crlf()
    session.write("2")
    session.crlf()
    session.write("3")
    session.crlf()
    session.write("4")
    session.crlf()

    session.setScrollingRegion(1, 3)

    session.cursorPosition(1, 2)

    session.deleteLines(20)

    session.assertScreenLines("1", "", "", "4")
  }

  @Test
  fun deleteCharacters() = session(15, 3) { session ->
    session.write("first line")
    session.crlf()
    session.write("second line")
    session.crlf()
    session.write("third line")

    session.assertScreenLines("first line", "second line", "third line")

    session.cursorPosition(1, 1)
    session.deleteCharacters(1)
    session.assertScreenLines("irst line", "second line", "third line")

    session.cursorPosition(6, 1)
    session.deleteCharacters(2)
    session.assertScreenLines("irst ne", "second line", "third line")

    session.cursorPosition(7, 2)
    session.deleteCharacters(42)
    session.assertScreenLines("irst ne", "second", "third line")

    session.cursorPosition(1, 3)
    session.deleteCharacters(6)
    session.assertScreenLines("irst ne", "second", "line")
  }

  @Test
  fun insertBlankCharacters() = session(10, 2) { session ->
    session.write("11111")

    session.cursorPosition(2, 1)
    session.insertBlankCharacters(2)

    session.assertScreenLines("1  1111")
    session.cursorPosition(6, 1)
    session.insertBlankCharacters(4)

    session.assertScreenLines("1  11    1")
  }
}

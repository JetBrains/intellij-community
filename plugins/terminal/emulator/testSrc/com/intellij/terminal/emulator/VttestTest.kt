// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.junit.jupiter.api.Test

/**
 * The classic `vttest` conformance screens, run non-interactively.
 *
 * `vttest` itself asks a human to eyeball each screen, so it cannot be a unit test. These cases are the
 * deterministic distillation of its first two menu entries — "Test of cursor movements" and "Test of
 * screen features" — as scripted by libvterm in `t/90vttest_01-movement-*.test` and
 * `t/90vttest_02-screen-*.test` (libvterm is MIT, © Paul Evans): each drives a fixed byte stream and then
 * asserts the exact 80x24 screen it must produce.
 *
 * Unlike the feature-focused tests in this package, these are *whole-screen* checks: one wrong column in
 * any of the sequences below shows up as a broken border or a shifted frame, which is what makes them a
 * useful smoke test of the emulator (and of a libghostty-vt upgrade).
 *
 * Rows are compared through [TerminalRow.toStyledText], whose text projection matches libvterm's
 * `vterm_screen_get_chars` exactly: an unwritten cell before the last glyph becomes a space, trailing
 * unwritten cells are dropped, and the spacer behind a double-width glyph is skipped.
 */
class VttestTest {

  /**
   * vttest 1.1: draws a border of `*` and `+` plus an inner frame of `E`s using absolute (CUP/HVP) and
   * relative (CUU/CUD/CUF/CUB, BS) motion, index/reverse-index/next-line (IND/RI/NEL) at the margins,
   * erasing (ED/EL) and DECALN, with `0` parameters exercised throughout (`CSI 0 A` == `CSI 1 A`).
   */
  @Test
  fun cursorMovements() = session(80, 24) { session ->
    session.write(esc("#8")) // DECALN: fill the whole screen with 'E'

    // Carve the screen back out with erases: ED below/above, EL to the right/left, full-line EL.
    session.write(csi("9;10H") + csi("1J"))
    session.write(csi("18;60H") + csi("0J") + csi("1K"))
    session.write(csi("9;71H") + csi("0K"))
    for (row in 10..16) {
      session.write(csi("$row;10H") + csi("1K") + csi("$row;71H") + csi("0K"))
    }
    session.write(csi("17;30H") + csi("2K"))

    // Top and bottom rows of '*', one HVP-addressed column at a time.
    for (column in 1..80) {
      session.write(csi("24;${column}f*") + csi("1;${column}f*"))
    }

    // Left column of '+', walking down with IND; right column, walking up with RI.
    session.write(csi("2;2H"))
    repeat(22) { session.write("+" + csi("1D") + esc("D")) }
    session.write(csi("23;79H"))
    repeat(22) { session.write("+" + csi("1D") + esc("M")) }

    // Left and right border columns of '*', one row per NEL.
    session.write(csi("2;1H"))
    for (row in 2..23) {
      session.write("*" + csi("$row;80H") + "*" + csi("10D") + esc("E"))
    }

    // Second row of '+', drawn with a no-op CUF 0 / CUB 2 / CUF 1 dance.
    session.write(csi("2;10H") + csi("42D") + csi("2C"))
    repeat(76) { session.write("+" + csi("0C") + csi("2D") + csi("1C")) }

    // Same for the second-to-last row, this time ending each step on a BS.
    session.write(csi("23;70H") + csi("42C") + csi("2D"))
    repeat(76) { session.write("+" + csi("1D") + csi("1C") + csi("0D") + "\b") }

    // Cursor-up and cursor-down must clamp at the screen edges, and `0` must behave as `1`.
    session.write(csi("1;1H") + csi("10A") + csi("1A") + csi("0A"))
    session.write(csi("24;80H") + csi("10B") + csi("1B") + csi("0B"))

    // Blank the inside of the E frame.
    session.write(csi("10;12H"))
    repeat(6) {
      repeat(58) { session.write(" ") }
      session.write(csi("1B") + csi("58D"))
    }

    session.write(csi("5A") + csi("1C") + "The screen should be cleared,  and have an unbroken bor-")
    session.write(csi("12;13H") + "der of *'s and +'s around the edge,   and exactly in the")
    session.write(csi("13;13H") + "middle  there should be a frame of E's around this  text")
    session.write(csi("14;13H") + "with  one (1) free position around it.    Push <RETURN>")

    session.assertScreenRow(0, "*".repeat(80))
    session.assertScreenRow(1, "*" + "+".repeat(78) + "*")
    for (row in 2..7) session.assertScreenRow(row, "*+" + " ".repeat(76) + "+*")
    session.assertScreenRow(8, "*+" + " ".repeat(8) + "E".repeat(60) + " ".repeat(8) + "+*")
    session.assertScreenRow(9, "*+" + " ".repeat(8) + "E" + " ".repeat(58) + "E" + " ".repeat(8) + "+*")
    session.assertScreenRow(10, "*+        E The screen should be cleared,  and have an unbroken bor- E        +*")
    session.assertScreenRow(11, "*+        E der of *'s and +'s around the edge,   and exactly in the E        +*")
    session.assertScreenRow(12, "*+        E middle  there should be a frame of E's around this  text E        +*")
    session.assertScreenRow(13, "*+        E with  one (1) free position around it.    Push <RETURN>  E        +*")
    session.assertScreenRow(14, "*+" + " ".repeat(8) + "E" + " ".repeat(58) + "E" + " ".repeat(8) + "+*")
    session.assertScreenRow(15, "*+" + " ".repeat(8) + "E".repeat(60) + " ".repeat(8) + "+*")
    for (row in 16..21) session.assertScreenRow(row, "*+" + " ".repeat(76) + "+*")
    session.assertScreenRow(22, "*" + "+".repeat(78) + "*")
    session.assertScreenRow(23, "*".repeat(80))
    session.assertCursorPosition(68, 14)
  }

  /**
   * vttest 1.2: writes a column of letters at the edges of a scrolling region (DECSTBM rows 3..21) with
   * origin mode (DECOM) on, so every address is region-relative and every LF at the bottom margin scrolls
   * the region. Autowrap at column 80, BS, and HT are exercised at the same time; after 26 letters only
   * the last 18 (I..Z) may survive inside the region.
   */
  @Test
  fun cursorMovementsInsideScrollRegion() = session(80, 24) { session ->
    session.write(csi("3;21r")) // DECSTBM
    session.write(csi("?6h"))   // DECOM

    for (group in listOf("ABCD", "EFGH", "IJKL", "MNOP", "QRST", "UVWX")) {
      val (first, second, third, fourth) = listOf(group[0], group[1], group[2], group[3])
      session.write(csi("19;1H") + first + csi("19;80H") + first.lowercaseChar() + "\n")
      session.write(csi("18;80H") + first.lowercaseChar() + second + csi("19;80H") + second + "\b " + second.lowercaseChar() + "\n")
      session.write(csi("19;80H") + third + "\b\b\t\t" + third.lowercaseChar() + csi("19;2H") + "\b" + third + "\n")
      session.write(csi("19;80H") + "\n" + csi("18;1H") + fourth + csi("18;80H") + fourth.lowercaseChar())
    }
    session.write(csi("19;1H") + "Y" + csi("19;80H") + "y" + "\n")
    session.write(csi("18;80H") + "yZ" + csi("19;80H") + "Z" + "\b z\n")

    for ((index, letter) in ('I'..'Z').withIndex()) {
      session.assertScreenRow(index + 2, letter + " ".repeat(78) + letter.lowercaseChar())
    }
    session.assertScreenRow(20, "")
    session.assertCursorPosition(80, 21)
  }

  /**
   * vttest 1.3: C0 control characters *inside* a CSI sequence must be executed as they arrive without
   * aborting the sequence, so `CSI 2 BS C` backspaces and then moves the cursor forward by two, and
   * `CSI 1 VT A` feeds a line and then moves back up. All four rows must come out identical.
   */
  @Test
  fun controlCharactersInsideEscapeSequences() = session(80, 24) { session ->
    session.write("A B C D E F G H I")
    session.write("\r\n")

    session.write(('A'..'I').joinToString(separator = csi("2\bC"))) // BS inside CSI
    session.write("\r\n")

    session.write("A ")
    for ((index, letter) in ('B'..'I').withIndex()) {
      session.write(csi("\r${(index + 1) * 2}C") + letter) // CR inside CSI
    }
    session.write("\r\n")

    for (letter in 'A'..'I') {
      session.write("$letter " + csi("1\u000BA")) // VT inside CSI: line feed, then CUU 1
    }

    for (row in 0..2) session.assertScreenRow(row, "A B C D E F G H I")
    session.assertScreenRow(3, "A B C D E F G H I ")
    session.assertCursorPosition(19, 4)
  }

  /** vttest 1.6: parameters padded with leading zeroes must parse to the same value. */
  @Test
  fun leadingZeroesInEscapeSequences() = session(80, 24) { session ->
    val sentence = "This is a correct sentence"
    for ((index, char) in sentence.withIndex()) {
      val row = "0".repeat(10) + "4"
      val column = "0".repeat(8) + (index + 1)
      session.write(csi("$row;${column}H") + char)
    }

    session.assertScreenRow(3, sentence)
  }

  /**
   * vttest 2.1: autowrap (DECAWM, mode 7). With wrapping on, 170 glyphs fill two rows and start a third;
   * with wrapping off, printing past column 80 keeps overwriting the last cell instead of moving on.
   */
  @Test
  fun autoWrapMode() = session(80, 24) { session ->
    session.write(csi("?7h"))
    repeat(170) { session.write("*") }

    session.write(csi("?7l") + csi("3;1H"))
    repeat(177) { session.write("*") }

    session.write(csi("?7h") + csi("5;1H") + "OK")

    for (row in 0..2) session.assertScreenRow(row, "*".repeat(80))
    session.assertScreenRow(3, "")
    session.assertScreenRow(4, "OK")
  }

  /**
   * vttest 2.2: tab stops. Clears all stops (TBC 3), sets one every 3 columns (HTS), clears every other
   * one (TBC 0) and checks that TBC 1 / TBC 2 are ignored, then tabs across the row: the glyphs must land
   * on the surviving stops, 6 columns apart.
   */
  @Test
  fun tabStopSettingAndClearing() = session(80, 24) { session ->
    session.write(csi("2J") + csi("3g"))

    session.write(csi("1;1H"))
    repeat(26) { session.write(csi("3C") + esc("H")) } // HTS every 3 columns

    session.write(csi("1;4H"))
    repeat(13) { session.write(csi("0g") + csi("6C")) } // TBC: drop every other stop

    session.write(csi("1;7H"))
    session.write(csi("1g") + csi("2g")) // unsupported TBC parameters: must not clear anything

    session.write(csi("1;1H"))
    repeat(13) { session.write("\t*") }

    session.write(csi("2;2H"))
    repeat(13) { session.write("     *") }

    val expected = " ".repeat(6) + "*" + (" ".repeat(5) + "*").repeat(12)
    session.expectFullRebuild() // the ED 2 above repaints the whole screen
    session.assertScreenRow(0, expected)
    session.assertScreenRow(1, expected)
    session.assertCursorPosition(80, 2)
  }

  /**
   * vttest 2.3: with origin mode (DECOM) on, `CSI 1;1H` addresses the top of the scrolling region rather
   * than the top of the screen, and LF at the bottom margin scrolls only the region.
   */
  @Test
  fun originModeInsideScrollRegion() = session(80, 24) { session ->
    session.write(csi("?6h"))
    session.write(csi("23;24r"))
    session.write("\n")
    session.write("Bottom")
    session.write(csi("1;1H"))
    session.write("Above")

    session.assertScreenRow(22, "Above")
    session.assertScreenRow(23, "Bottom")
  }

  /** vttest 2.3 (continued): with origin mode off, addressing stays absolute despite the scrolling region. */
  @Test
  fun absoluteAddressingWithScrollRegion() = session(80, 24) { session ->
    session.write(csi("?6l"))
    session.write(csi("23;24r"))
    session.write(csi("24;1H"))
    session.write("Bottom")
    session.write(csi("1;1H"))
    session.write("Top")

    session.assertScreenRow(0, "Top")
    session.assertScreenRow(23, "Bottom")
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.junit.jupiter.api.Test

/**
 * Where every cursor-movement sequence leaves the cursor: the C0 controls (BS, HT, CR, LF), the single-shift
 * escapes (IND, RI, NEL), the CSI movements (CUU/CUD/CUF/CUB, CNL/CPL, CHA/CUP/HVP, HPA/HPR, VPA/VPR,
 * CHT/CBT), their edge clamping, and the "phantom" deferred-wrap state at the right margin.
 *
 * Scenarios ported from libvterm's `t/11state_movecursor.test` (MIT, © Paul Evans). Each test mirrors one
 * of that script's reset-delimited sections, so the steps inside a test build on each other exactly as they
 * do there. Cursor positions are asserted 1-based here, 0-based in the original.
 *
 * Style, erasing and editing at the cursor live in [CursorTest], [EraseTest] and [TextBufferTest].
 */
class CursorMovementTest {

  @Test
  fun controlCharactersMoveTheCursor() = session(80, 25) { session ->
    session.write("ABC")
    session.assertCursorPosition(4, 1)
    session.write("\b")
    session.assertCursorPosition(3, 1)
    session.write("\t")
    session.assertCursorPosition(9, 1)
    session.write("\r")
    session.assertCursorPosition(1, 1)
    session.write("\n")
    session.assertCursorPosition(1, 2)
  }

  @Test
  fun backspaceIsBoundedByTheLeftEdge() = session(80, 25) { session ->
    session.write(csi("4;2H"))
    session.assertCursorPosition(2, 4)
    session.write("\b")
    session.assertCursorPosition(1, 4)
    session.write("\b") // already at the left edge: stays put
    session.assertCursorPosition(1, 4)
  }

  /**
   * Printing in the last column leaves the cursor there with a pending wrap ("phantom") rather than moving
   * it off-screen; a backspace cancels that pending wrap and steps back one real column.
   */
  @Test
  fun backspaceCancelsPendingWrap() = session(80, 25) { session ->
    session.write(csi("4;80H"))
    session.assertCursorPosition(80, 4)
    session.write("X")
    session.assertCursorPosition(80, 4)
    session.write("\b")
    session.assertCursorPosition(79, 4)
  }

  @Test
  fun horizontalTabIsBoundedByTheRightEdge() = session(80, 25) { session ->
    session.write(csi("1;78H"))
    session.assertCursorPosition(78, 1)
    session.write("\t")
    session.assertCursorPosition(80, 1)
    session.write("\t") // no tab stop past the last column: stays put
    session.assertCursorPosition(80, 1)
  }

  @Test
  fun indexReverseIndexAndNextLine() = session(80, 25) { session ->
    session.write("ABC" + esc("D")) // IND: down one row, column kept
    session.assertCursorPosition(4, 2)
    session.write(esc("M"))         // RI: up one row, column kept
    session.assertCursorPosition(4, 1)
    session.write(esc("E"))         // NEL: down one row, to column 1
    session.assertCursorPosition(1, 2)
  }

  /** For all four relative movements a `0` parameter behaves as `1`, and an omitted parameter as `1`. */
  @Test
  fun relativeCursorMovements() = session(80, 25) { session ->
    session.write(csi("B"))  // CUD
    session.assertCursorPosition(1, 2)
    session.write(csi("3B"))
    session.assertCursorPosition(1, 5)
    session.write(csi("0B"))
    session.assertCursorPosition(1, 6)

    session.write(csi("C"))  // CUF
    session.assertCursorPosition(2, 6)
    session.write(csi("3C"))
    session.assertCursorPosition(5, 6)
    session.write(csi("0C"))
    session.assertCursorPosition(6, 6)

    session.write(csi("A"))  // CUU
    session.assertCursorPosition(6, 5)
    session.write(csi("3A"))
    session.assertCursorPosition(6, 2)
    session.write(csi("0A"))
    session.assertCursorPosition(6, 1)

    session.write(csi("D"))  // CUB
    session.assertCursorPosition(5, 1)
    session.write(csi("3D"))
    session.assertCursorPosition(2, 1)
    session.write(csi("0D"))
    session.assertCursorPosition(1, 1)
  }

  /** CNL / CPL move whole lines and always land in column 1. */
  @Test
  fun cursorNextAndPreviousLine() = session(80, 25) { session ->
    session.write("   ")
    session.assertCursorPosition(4, 1)
    session.write(csi("E"))  // CNL
    session.assertCursorPosition(1, 2)
    session.write("   ")
    session.write(csi("2E"))
    session.assertCursorPosition(1, 4)
    session.write(csi("0E"))
    session.assertCursorPosition(1, 5)

    session.write("   ")
    session.write(csi("F"))  // CPL
    session.assertCursorPosition(1, 4)
    session.write("   ")
    session.write(csi("2F"))
    session.assertCursorPosition(1, 2)
    session.write(csi("0F"))
    session.assertCursorPosition(1, 1)
  }

  @Test
  fun absoluteCursorPositioning() = session(80, 25) { session ->
    session.write("\n")
    session.write(csi("20G")) // CHA
    session.assertCursorPosition(20, 2)
    session.write(csi("G"))
    session.assertCursorPosition(1, 2)

    session.write(csi("10;5H")) // CUP
    session.assertCursorPosition(5, 10)
    session.write(csi("8H"))
    session.assertCursorPosition(1, 8)
    session.write(csi("H"))
    session.assertCursorPosition(1, 1)
  }

  /** Re-addressing the cursor cancels a pending wrap, so the next glyph lands on the same row. */
  @Test
  fun cursorPositionCancelsPendingWrap() = session(80, 25) { session ->
    session.write(csi("10;78H"))
    session.assertCursorPosition(78, 10)
    session.write("ABC")
    session.assertCursorPosition(80, 10) // pending wrap, not column 81
    session.write(csi("10;80H"))         // cancels it
    session.write("C")
    session.assertCursorPosition(80, 10) // pending wrap again
    session.write("X")                   // now it takes effect
    session.assertCursorPosition(2, 11)
  }

  @Test
  fun movementIsClampedToTheScreen() = session(80, 25) { session ->
    session.write(csi("A"))
    session.assertCursorPosition(1, 1)
    session.write(csi("D"))
    session.assertCursorPosition(1, 1)

    session.write(csi("25;80H"))
    session.assertCursorPosition(80, 25)
    session.write(csi("B"))
    session.assertCursorPosition(80, 25)
    session.write(csi("C"))
    session.assertCursorPosition(80, 25)
    session.write(csi("E"))
    session.assertCursorPosition(1, 25)

    session.write(csi("H"))
    session.assertCursorPosition(1, 1)
    session.write(csi("F"))
    session.assertCursorPosition(1, 1)

    session.write(csi("999G"))
    session.assertCursorPosition(80, 1)
    session.write(csi("99;99H"))
    session.assertCursorPosition(80, 25)
  }

  /** The position-oriented aliases of the movement sequences: HPA/HPR and VPA/VPR, plus HVP. */
  @Test
  fun horizontalAndVerticalPositioning() = session(80, 25) { session ->
    session.write(csi("5`")) // HPA
    session.assertCursorPosition(5, 1)
    session.write(csi("3a")) // HPR
    session.assertCursorPosition(8, 1)
    session.write(csi("3j")) // HPB
    session.assertCursorPosition(5, 1)

    session.write(csi("3;3f")) // HVP
    session.assertCursorPosition(3, 3)

    session.write(csi("5d")) // VPA
    session.assertCursorPosition(3, 5)
    session.write(csi("2e")) // VPR
    session.assertCursorPosition(3, 7)
    session.write(csi("2k")) // VPB
    session.assertCursorPosition(3, 5)
  }

  @Test
  fun tabStopsEveryEightColumns() = session(80, 25) { session ->
    session.write("\t")
    session.assertCursorPosition(9, 1)
    session.write("   ")
    session.assertCursorPosition(12, 1)
    session.write("\t")
    session.assertCursorPosition(17, 1)
    session.write("       ")
    session.assertCursorPosition(24, 1)
    session.write("\t") // one column short of the next stop
    session.assertCursorPosition(25, 1)
    session.write("        ")
    session.assertCursorPosition(33, 1)
    session.write("\t")
    session.assertCursorPosition(41, 1)

    session.write(csi("I")) // CHT: forward one tab stop
    session.assertCursorPosition(49, 1)
    session.write(csi("2I"))
    session.assertCursorPosition(65, 1)

    session.write(csi("Z")) // CBT: back one tab stop
    session.assertCursorPosition(57, 1)
    session.write(csi("2Z"))
    session.assertCursorPosition(41, 1)
  }
}

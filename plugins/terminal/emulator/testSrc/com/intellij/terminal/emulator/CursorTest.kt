// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.junit.jupiter.api.Test

/**
 * Everything about the text cursor, driven through the [TerminalEmulator] API: how printing/movement
 * clamps it inside the screen, how its position is reported (DSR), and its drawing style — shape and
 * blink — as selected via DECSCUSR (`CSI Ps SP q`) and DEC private mode 12.
 */
class CursorTest {

  @Test
  fun cursorIsLockedInsideScreenLeftEdge() = session(80, 24) { session ->
    session.write(csi("1;1H") + "foo" + "\b\b\b\b" + "bar") // 4 backspaces cannot pass column 0
    session.assertScreenLines("bar")
  }

  @Test
  fun cursorIsLockedInsideScreenTopEdge() = session(80, 24) { session ->
    session.write("\r" + csi("2d") + "Ready")  // VPA to row 2
    session.write("\r" + csi("1d") + "Steady") // VPA to row 1
    session.write("\r" + csi("0d") + "Go")     // VPA to row 0 -> clamped to row 1
    session.assertScreenLines("Goeady", "Ready")
  }

  @Test
  fun deviceStatusReportWithOriginMode() = session(80, 24) { session ->
    session.write(csi("2;23r")) // scroll region rows 2..23
    session.write(csi("?6h"))   // origin mode (DECOM)
    session.write(csi("1;1H"))  // relative to the scroll region -> absolute row 2
    session.assertCursorPosition(1, 2)

    session.write(csi("6n"))    // DSR - report cursor position (region-relative)
    session.assertResponses(csi("1;1R"))
  }

  @Test
  fun answersDeviceStatusReportViaWritePty() = session(20, 5) { session ->
    session.write("ab" + csi("6n")) // DSR: report cursor position

    // CSI row ; col R  -> cursor is at row 1, col 3 (after "ab").
    session.assertResponses(csi("1;3R"))
  }

  /**
   * DECSCUSR (`CSI Ps SP q`) packs both the cursor *shape* (by group: 1/2 → block, 3/4 → underline,
   * 5/6 → bar) and the *blink* state (by parity: odd blinks, even is steady) into one parameter.
   */
  @Test
  fun cursorShapeAndBlinkFollowDecscusr() = session(20, 3) { session ->
    session.assertCursorStyle(CursorShape.BLOCK, blinking = false) // default before any DECSCUSR

    session.write(csi("1 q"))
    session.assertCursorStyle(CursorShape.BLOCK, blinking = true)
    session.write(csi("2 q"))
    session.assertCursorStyle(CursorShape.BLOCK, blinking = false)
    session.write(csi("3 q"))
    session.assertCursorStyle(CursorShape.UNDERLINE, blinking = true)
    session.write(csi("4 q"))
    session.assertCursorStyle(CursorShape.UNDERLINE, blinking = false)
    session.write(csi("5 q"))
    session.assertCursorStyle(CursorShape.BAR, blinking = true)
    session.write(csi("6 q"))
    session.assertCursorStyle(CursorShape.BAR, blinking = false)
  }

  /**
   * `CSI 0 SP q` resets the cursor to the configured default (block, not blinking).
   */
  @Test
  fun cursorShapeResetToDefault() = session(20, 3) { session ->
    session.write(csi("3 q")) // blinking underline
    session.assertCursorStyle(CursorShape.UNDERLINE, blinking = true)

    session.write(csi("0 q")) // reset -> default
    session.assertCursorStyle(CursorShape.BLOCK, blinking = false)
  }

  /**
   * DEC private mode 12 (att610) toggles cursor blinking independently of the shape: `CSI ?12h` starts
   * blinking, `CSI ?12l` stops it. Ghostty reflects it in the same render-state blink flag as DECSCUSR
   * ([TerminalEmulator.cursorBlinking]) while leaving [TerminalEmulator.cursorShape] untouched.
   */
  @Test
  fun cursorBlinkFollowsDecPrivateMode12() = session(20, 3) { session ->
    session.write(csi("2 q")) // steady block
    session.assertCursorStyle(CursorShape.BLOCK, blinking = false)

    session.write(csi("?12h")) // start blinking; shape unchanged
    session.assertCursorStyle(CursorShape.BLOCK, blinking = true)

    session.write(csi("?12l")) // stop blinking
    session.assertCursorStyle(CursorShape.BLOCK, blinking = false)

    session.write(csi("?12h")) // start again
    session.assertCursorStyle(CursorShape.BLOCK, blinking = true)
  }
}

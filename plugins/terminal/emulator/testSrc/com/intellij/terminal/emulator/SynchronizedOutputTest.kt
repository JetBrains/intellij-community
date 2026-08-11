// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * DEC mode 2026 (synchronized output).
 *
 * The mode is a *presentation* hint, not a model lock: the engine keeps applying input to the grid
 * while the block is open, and the only thing it offers the embedder is
 * [TerminalEmulator.synchronizedOutput]. It is the embedder that must hold back the frame — exactly the
 * split ghostty itself uses, where the VT engine stays live and the renderer skips painting while the
 * mode is set. Tests here pin both halves of that contract:
 *
 *  - the flag itself: set on `CSI ?2026h`, cleared on `CSI ?2026l`, and cleared by the recovery paths
 *    (RIS, resize) that exist so a program which never closes its block cannot wedge the display;
 *  - that the *model* keeps moving inside the block — see [modelKeepsAdvancingInsideTheBlock] and
 *    [changeTrackingStillReportsRowsInsideTheBlock]. A renderer that only watches
 *    [TerminalEmulator.takeChanges] and ignores the flag will show partial frames.
 *
 * The remaining tests write a whole block and assert the resulting screen: deferring presentation must
 * never change the content that eventually lands.
 */
class SynchronizedOutputTest {

  // ---- the mode flag ----

  @Test
  fun flagIsSetInsideTheBlockAndClearedOnEnd() = session(20, 5) { session ->
    assertThat(session.synchronizedOutput).describedAs("mode 2026 is off by default").isFalse()
    session.write(BEGIN)
    assertThat(session.synchronizedOutput).describedAs("inside the block").isTrue()
    session.write("content")
    assertThat(session.synchronizedOutput).describedAs("output does not close the block").isTrue()
    session.write(END)
    assertThat(session.synchronizedOutput).describedAs("after the block").isFalse()
  }

  /**
   * A PTY read can split a sequence anywhere, so the block must be driven by the parser's accumulated
   * state rather than by whatever a single [TerminalEmulator.write] happens to contain.
   */
  @Test
  fun flagSurvivesSequencesSplitAcrossWrites() = session(20, 5) { session ->
    session.write(csi("?20"))
    assertThat(session.synchronizedOutput).describedAs("half of BEGIN must not open the block").isFalse()
    session.write("26h")
    assertThat(session.synchronizedOutput).describedAs("BEGIN completed by the next write").isTrue()

    session.write("A")
    session.write("B")
    assertThat(session.synchronizedOutput).describedAs("the block spans writes").isTrue()

    session.write(csi("?2026"))
    assertThat(session.synchronizedOutput).describedAs("half of END must not close the block").isTrue()
    session.write("l")
    assertThat(session.synchronizedOutput).isFalse()
    session.assertScreenLines("AB")
  }

  /**
   * There is no watchdog in the emulator: a program that opens a block and dies leaves the flag set
   * forever. Ghostty's own app layer arms a 1000 ms timer to force the mode off (`sync_reset_ms` in
   * `termio/Thread.zig`); an embedder of this engine needs the same safety net, so pin the raw behavior.
   */
  @Test
  fun blockNeverExpiresOnItsOwn() = session(20, 5) { session ->
    session.write(BEGIN + "partial frame")
    assertThat(session.synchronizedOutput).isTrue()
    repeat(100) { session.write("") } // further (empty) input does not time the block out
    assertThat(session.synchronizedOutput).describedAs("no watchdog: the embedder owns the timeout").isTrue()
  }

  /** Mode 2026 is a plain mode, not a counter: nesting does not stack, and a stray END is harmless. */
  @Test
  fun blocksDoNotNest() = session(20, 5) { session ->
    session.write(BEGIN)
    session.write(BEGIN)
    session.write(END)
    assertThat(session.synchronizedOutput).describedAs("one END closes a doubly-begun block").isFalse()
    session.write(END)
    assertThat(session.synchronizedOutput).describedAs("a stray END is a no-op").isFalse()
  }

  /** Apps feature-detect mode 2026 with DECRQM before using it, so the reported state must be accurate. */
  @Test
  fun decrqmReportsTheModeState() = session(20, 5) { session ->
    session.write(DECRQM)
    session.write(BEGIN)
    session.write(DECRQM)
    session.write(END)
    session.write(DECRQM)
    // 2 = reset, 1 = set (a mode the terminal did not recognize would answer 0).
    session.assertResponses(csi($$"?2026;2$y"), csi($$"?2026;1$y"), csi($$"?2026;2$y"))
  }

  // ---- recovery paths that release a block ----

  @Test
  fun fullResetClearsTheBlock() = session(20, 5) { session ->
    session.write(BEGIN + "partial")
    session.expectFullRebuild()
    session.write(esc("c")) // RIS
    assertThat(session.synchronizedOutput).describedAs("RIS releases the block").isFalse()
    session.assertScreenLines()
  }

  /** A resize forces a repaint, so ghostty deliberately drops the block on every valid resize. */
  @Test
  fun resizeClearsTheBlock() = session(20, 5) { session ->
    session.write(BEGIN + "partial")
    assertThat(session.synchronizedOutput).isTrue()
    session.resize(10, 4)
    assertThat(session.synchronizedOutput).describedAs("resize releases the block").isFalse()
    session.assertScreenLines("partial")
  }

  // ---- the model is NOT frozen by the block ----

  /**
   * The whole point of this test: opening a block does *not* freeze the terminal model. Every read the
   * embedder can make — screen text, cursor, scrollback, the alternate-screen flag — reflects the input
   * as soon as it is written, mid-block. Only presentation is meant to be deferred, and only by the
   * embedder, which is why [TerminalEmulator.synchronizedOutput] exists.
   */
  @Test
  fun modelKeepsAdvancingInsideTheBlock() = session(20, 3) { session ->
    session.write(BEGIN)

    session.write("L1")
    session.assertScreenLines("L1")
    session.assertCursorPosition(3, 1)

    // Scroll past the 3-row screen: scrollback grows inside the block too.
    session.crlf()
    session.writeLinesWithCrlf(listOf("L2", "L3", "L4", "L5", "L6"), addCrlfAfterLast = true)
    session.expectFullRebuild() // a scroll repaints the whole screen, block or no block
    session.assertScreenLines("L5", "L6")
    session.assertScrollbackLines("L1", "L2", "L3", "L4")
    session.assertCursorPosition(1, 3)

    // Screen switches take effect immediately too.
    session.write(csi("?1049h"))
    assertThat(session.usingAlternateScreen).describedAs("mode changes apply inside the block").isTrue()
    session.write(csi("?1049l"))
    assertThat(session.usingAlternateScreen).isFalse()

    assertThat(session.synchronizedOutput).describedAs("still inside the block the whole time").isTrue()
    session.write(END)

    // Closing the block flushes nothing: the model already held everything.
    session.expectFullRebuild() // ...the alternate-screen round trip did repaint, though
    session.assertScreenLines("L5", "L6")
    session.assertScrollbackLines("L1", "L2", "L3", "L4")
  }

  /**
   * Change tracking is not gated on the mode either: [TerminalEmulator.takeChanges] keeps handing out
   * dirty rows inside a block. So a renderer must check [TerminalEmulator.synchronizedOutput] before
   * presenting — polling `takeChanges()` alone would paint the half-built frame.
   *
   * Consumes [EmulatorTestSession.takeChanges] directly, so (like [ChangeTrackingTest]) this test does
   * not use the screen assertions, whose incremental mirror needs the same deltas.
   */
  @Test
  fun changeTrackingStillReportsRowsInsideTheBlock() = session(20, 5) { session ->
    session.write("seed")
    session.takeChanges() // consume the initial paint

    session.write(BEGIN)
    assertThat(session.takeChanges()).describedAs("opening a block changes no cell").isEqualTo(ScreenChange.None)

    // Row 0 comes along because the cursor left it: the render state always redraws the row the cursor
    // moved off and the row it moved to.
    session.write(csi("2;1H") + "second row")
    assertThat(session.takeChanges())
      .describedAs("the edited row is reported while the block is still open")
      .isEqualTo(ScreenChange.Rows(intArrayOf(0, 1)))

    session.write(csi("3;1H") + "third row")
    assertThat(session.takeChanges()).isEqualTo(ScreenChange.Rows(intArrayOf(1, 2)))

    session.write(END)
    assertThat(session.takeChanges())
      .describedAs("closing the block replays nothing: the rows were already reported")
      .isEqualTo(ScreenChange.None)
  }

  /** Side effects the host must see are not held back either. */
  @Test
  fun hostVisibleEffectsAreNotDeferred() = session(20, 5) { session ->
    session.write(BEGIN)
    session.write(BELL_CHAR.toString())
    session.assertBellCount(1)
    session.write(csi("6n")) // DSR: cursor position report
    session.assertResponses(csi("1;1R"))
    session.write(END)
  }

  // ---- content written inside a block lands unchanged ----

  @Test
  fun basicSynchronizedOutput() = session(20, 5) { session ->
    session.write("Before" + BEGIN + "Sync" + END + "After")
    session.assertScreenLines("BeforeSyncAfter")
  }

  @Test
  fun synchronizedOutputWithNewlines() = session(20, 5) { session ->
    session.write("Line1\r\n" + BEGIN + "Line2\r\nLine3" + END + "\r\nLine4")
    session.assertScreenLines("Line1", "Line2", "Line3", "Line4")
  }

  @Test
  fun synchronizedOutputWithControlSequences() = session(20, 5) { session ->
    session.write(BEGIN + csi("1;1H") + "First" + csi("2;1H") + "Second" + END)
    session.assertScreenLines("First", "Second")
  }

  @Test
  fun multipleSynchronizedOutputBlocks() = session(20, 5) { session ->
    session.write("A" + BEGIN + "B" + END + "C" + BEGIN + "D" + END + "E")
    session.assertScreenLines("ABCDE")
  }

  @Test
  fun emptySynchronizedOutputBlock() = session(20, 5) { session ->
    session.write("Before" + BEGIN + END + "After")
    session.assertScreenLines("BeforeAfter")
  }

  @Test
  fun doubleBeginCsi() = session(20, 5) { session ->
    session.write(BEGIN + "Foo\r\n" + BEGIN + "Bar" + END)
    session.assertScreenLines("Foo", "Bar")
  }

  @Test
  fun synchronizedOutputWithCursorMovement() = session(20, 5) { session ->
    session.write(BEGIN + "Hello" + csi("1;1H") + "X" + END)
    session.assertScreenLines("Xello")
  }

  @Test
  fun synchronizedOutputWithColors() = session(20, 5) { session ->
    session.write(BEGIN + csi("31m") + "Red" + csi("0m") + " Normal" + END)
    session.assertScreenLines("Red Normal")
  }

  @Test
  fun synchronizedOutputWithBackspace() = session(20, 5) { session ->
    session.write("Foo" + BEGIN + "Bar\b\b\b\b1234" + END)
    session.assertScreenLines("Fo1234")
  }

  @Test
  fun noEndSequenceBeforeFinish() = session(20, 5) { session ->
    session.write("Foo" + BEGIN + "Bar")
    session.assertScreenLines("FooBar")
  }

  companion object {
    private val BEGIN: String = csi("?2026h")
    private val END: String = csi("?2026l")

    /** DECRQM: ask the terminal whether mode 2026 is currently set. */
    private val DECRQM: String = csi($$"?2026$p")
  }
}

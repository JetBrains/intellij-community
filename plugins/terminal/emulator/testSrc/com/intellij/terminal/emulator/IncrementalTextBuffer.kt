// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat

/**
 * A view of the terminal screen + scrollback reconstructed ONLY from [TerminalEmulator]'s incremental
 * signals: [TerminalEmulator.takeChanges] for active-screen row deltas, and [TerminalEmulator.scrollbackRows]
 * growth for scrollback (which has no per-line change API). [EmulatorTestSession] validates this mirror
 * against a naive full read in every assert, so each assertion also exercises the change-tracking contract —
 * most importantly that a real change is never dropped (a missed change leaves this mirror stale).
 *
 * Usage: call [onResize] whenever the terminal is resized, [sync] once before reading, then read the
 * reconstructed view via [screenLines] / [scrollbackLines].
 */
internal class IncrementalTextBuffer(private val emulator: TerminalEmulator) {

  private val screen: MutableList<String> = ArrayList()
  private val scrollback: MutableList<String> = ArrayList()
  private var scrollbackCount: Int = 0
  private var pendingResize: Boolean = false
  private var fullRebuildExpected: Boolean = false

  // Full text buffer: every line ever finalized into history — INCLUDING lines the emulator has since evicted
  // from its bounded scrollback — accumulated via a HistoryMark, whose finalized-line count stays exact past the
  // scrollback cap (where the raw scrollbackRows delta plateaus). This is the "append the scrolled-off lines to a
  // growing document" model an editor-backed renderer uses.
  private val historyMark: HistoryMark = emulator.markHistoryBoundary()
  private val fullScrollback: MutableList<String> = ArrayList()

  /** Reflow can move content between the screen and scrollback, so a resize forces a full rebuild. */
  fun onResize() {
    pendingResize = true
  }

  /**
   * Approves the single upcoming [sync] to rebuild the whole active screen. A full rebuild means the
   * emulator reported a whole-screen change ([ScreenChange.All]) or a resize — the expensive,
   * non-incremental path a renderer wants to avoid. [sync] fails if it performs a full rebuild without
   * this approval, so tests must call this before an assertion whose change genuinely needs one (resize,
   * clear/reset, alternate-screen switch, first paint, …), catching unexpected full rebuilds. One-shot:
   * reset after each [sync].
   */
  fun expectFullRebuild() {
    fullRebuildExpected = true
  }

  /**
   * Advances the mirror from the emulator's incremental signals. Consumes [TerminalEmulator.takeChanges]
   * exactly once (first), then reads the reported rows — reads are read-only and don't re-dirty the next
   * poll. Call once before reading so the mirror reflects all input since the previous sync.
   */
  fun sync() {
    val change = emulator.takeChanges()
    val rows = emulator.size.rows
    if (pendingResize || screen.size != rows || change == ScreenChange.All) {
      // Whole active screen (or its size) changed: rebuild every row. This is the expensive path a
      // renderer wants to avoid, so require the test to have approved it via expectFullRebuild().
      assertThat(fullRebuildExpected).describedAs {
        "Unexpected full screen rebuild (pendingResize=$pendingResize, sizeChanged=${screen.size != rows}, " +
        "change=$change). A whole-screen repaint happened where an incremental (per-row) update was " +
        "expected. If this is genuinely a full-rebuild scenario (resize, clear/reset, alternate-screen " +
        "switch), call EmulatorTestSession.expectFullRebuild() before the assertion."
      }.isTrue()
      screen.clear()
      screen.addAll((0 until rows).map {
        emulator.screenLine(it).toStyledText().text
      })
    }
    else {
      // No full rebuild happened, so an approval here is stale: the test expected a whole-screen repaint
      // that the emulator did not report. Fail so the unnecessary expectFullRebuild() call is removed.
      assertThat(fullRebuildExpected).describedAs {
        "expectFullRebuild() was approved but no full screen rebuild happened (change=$change). Remove the " +
        "unnecessary approval (the change was incremental/none, or a preceding assertion already consumed it)."
      }.isFalse()
      if (change is ScreenChange.Rows) {
        for (y in change.rows) {
          if (y in 0 until rows) screen[y] = emulator.screenLine(y).toStyledText().text
        }
      }
      // ScreenChange.None: leave the mirror untouched — a missed change then surfaces as a mismatch.
    }
    fullRebuildExpected = false // one-shot: approval covers only the sync it precedes

    val cur = emulator.scrollbackRows
    // Scrollback has no per-line change API: normally only new lines appear at the end, so append them.
    // Reflow (resize), clear/reset (count shrinks), and capacity eviction (the oldest line is dropped, so
    // the front line's text changes) are not suffix-appends -> re-read the whole scrollback in those cases.
    val frontDropped = scrollbackCount > 0 && cur > 0 &&
                       emulator.scrollbackLine(0).toStyledText().text != scrollback[0]
    if (pendingResize || cur < scrollbackCount || frontDropped) {
      scrollback.clear()
      for (i in 0 until cur) scrollback.add(emulator.scrollbackLine(i).toStyledText().text)
    }
    else {
      for (i in scrollbackCount until cur) scrollback.add(emulator.scrollbackLine(i).toStyledText().text)
    }
    scrollbackCount = cur

    updateFullScrollback(cur)

    pendingResize = false
  }

  /**
   * Accumulates the full text buffer ([fullScrollback]) from the [HistoryMark]: appends the lines finalized into
   * history since the previous sync (the newest [HistoryMark.finalizedLineCount] scrollback rows), so it grows past
   * what the emulator retains. Reflow (resize), clear/reset (count shrinks) and mark eviction ([HistoryMark] returns
   * -1 when a single write outran the whole retained scrollback) can't be expressed as a suffix append, so re-sync
   * to the retained window there — the same degradation the bounded [scrollback] mirror makes.
   *
   * Also asserts the core [HistoryMark] contract on every sync: the emulator's retained scrollback is exactly the
   * tail of the accumulated history. This cross-checks the mark's finalized-line count against the independently
   * tracked [scrollback] mirror (itself validated against a naive full read by [EmulatorTestSession]).
   */
  private fun updateFullScrollback(cur: Int) {
    val finalized = historyMark.finalizedLineCount()
    if (pendingResize || finalized < 0) {
      // Reflow (resize) or a mark eviction (finalized < 0: a single write outran the whole retained scrollback)
      // can't be a clean suffix append -> re-sync to the retained window, degrading like the bounded [scrollback]
      // mirror. Note: ordinary page-based eviction is NOT such a case -- scrollbackRows drops in steps as whole
      // pages are pruned, but the mark tracks the boundary content, so [HistoryMark.finalizedLineCount] stays
      // correct across those drops and the append path below still holds.
      fullScrollback.clear()
      for (i in 0 until cur) fullScrollback.add(emulator.scrollbackLine(i).toStyledText().text)
    }
    else {
      for (i in cur - finalized until cur) fullScrollback.add(emulator.scrollbackLine(i).toStyledText().text)
    }
    historyMark.reset()

    assertThat(fullScrollback.takeLast(cur)).describedAs {
      "The retained scrollback must equal the tail of the accumulated full history (finalized=$finalized, " +
      "retained=$cur, fullSize=${fullScrollback.size}): the HistoryMark's finalized-line count disagrees with " +
      "the scrollbackRows-delta mirror."
    }.isEqualTo(scrollback)
  }

  /** Releases the native [HistoryMark]. Call when the owning [EmulatorTestSession] is closed. */
  fun close() {
    historyMark.close()
  }

  /** Active-screen lines with trailing empty rows dropped (mirrors [EmulatorTestSession]'s naive read). */
  fun screenLines(): List<String> = screen.subList(0, screen.indexOfLast { it.isNotEmpty() } + 1).toList()

  /**
   * The mirrored active-screen row [row] (0-based), as of the last [sync]. Unlike [screenLines] this keeps
   * trailing empty rows addressable, so a whole-screen check can assert every row of the grid.
   */
  fun screenRow(row: Int): String = screen[row]

  /** Scrollback lines, oldest first (the emulator's bounded retained window). */
  fun scrollbackLines(): List<String> = scrollback.toList()

  /**
   * The full text buffer's history: every line ever finalized into scrollback, oldest first — INCLUDING lines the
   * emulator has since evicted from its bounded scrollback. Accumulated via [TerminalEmulator.markHistoryBoundary];
   * a reflow (resize) or clear/reset drops it back to the currently retained window (see [updateFullScrollback]).
   */
  fun fullScrollbackLines(): List<String> = fullScrollback.toList()

  /** The full text buffer: the entire finalized history ([fullScrollbackLines]) followed by the active [screenLines]. */
  fun fullBufferLines(): List<String> = fullScrollback + screenLines()
}

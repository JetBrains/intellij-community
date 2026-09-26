// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * `OSC 9;4` progress reports (the ConEmu progress extension): `ESC ] 9 ; 4 ; <state> [; <percent>] <terminator>`.
 * The engine parses these and hands them to the emulator through its progress-report effect, which surfaces
 * them as polled state via [TerminalEmulator.progress]. These tests therefore also pin down the engine-side
 * parsing quirks the API inherits (defaults, clamping, and which malformed forms are not reports at all).
 */
class ProgressTest {

  @Test
  fun noProgressUntilReported() = session(20, 3) { session ->
    session.write("hello")
    assertThat(session.progress).isNull()
  }

  /** The typical run of a long-running command: repeated `9;4;1;<percent>` reports as the work advances. */
  @Test
  fun determinateProgressAdvances() = session(20, 3) { session ->
    session.write(osc("9;4;1;0"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, 0))

    session.write(osc("9;4;1;42"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, 42))

    session.write(osc("9;4;1;100"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, 100))
  }

  /** `9;4;0` removes the bar; the emulator models "no progress" as a null [TerminalEmulator.progress]. */
  @Test
  fun removeClearsProgress() = session(20, 3) { session ->
    session.write(osc("9;4;1;30"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, 30))

    session.write(osc("9;4;0"))
    assertThat(session.progress).isNull()
  }

  /** A percentage on the remove form is ignored: `9;4;0;100` still clears rather than reporting 100%. */
  @Test
  fun removeIgnoresPercent() = session(20, 3) { session ->
    session.write(osc("9;4;1;30"))
    session.write(osc("9;4;0;100"))
    assertThat(session.progress).isNull()
  }

  @Test
  fun errorState() = session(20, 3) { session ->
    session.write(osc("9;4;2;70"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.ERROR, 70))
  }

  @Test
  fun pausedState() = session(20, 3) { session ->
    session.write(osc("9;4;4;55"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.PAUSED, 55))
  }

  /**
   * An indeterminate report carries no completion fraction, so [TerminalProgress.percent] is null even when
   * the program supplies one.
   */
  @Test
  fun indeterminateStateHasNoPercent() = session(20, 3) { session ->
    session.write(osc("9;4;3"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.INDETERMINATE, null))

    session.write(osc("9;4;3;50"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.INDETERMINATE, null))
  }

  /**
   * Percentage defaults per state when the `;<percent>` part is missing entirely: a determinate report means
   * 0%, while error / paused simply have no percentage.
   */
  @Test
  fun omittedPercent() = session(20, 3) { session ->
    session.write(osc("9;4;1"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, 0))

    session.write(osc("9;4;2"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.ERROR, null))

    session.write(osc("9;4;4"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.PAUSED, null))
  }

  /** A percentage above 100 clamps rather than being rejected. */
  @Test
  fun percentAboveHundredClamps() = session(20, 3) { session ->
    session.write(osc("9;4;1;101"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, 100))

    session.write(osc("9;4;1;900"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, 100))
  }

  /**
   * A present-but-unparseable percentage degrades to "no percentage" instead of invalidating the report: the
   * state still applies. Note that this makes `9;4;1;` (an empty percentage) differ from `9;4;1` (which means
   * 0%). The last case is the engine's numeric limit: a value that overflows a 64-bit unsigned int is rejected
   * like any other unparseable one rather than clamping to 100.
   */
  @Test
  fun unparseablePercentKeepsTheState() = session(20, 3) { session ->
    session.write(osc("9;4;1;"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, null))

    session.write(osc("9;4;1;abc"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, null))

    session.write(osc("9;4;1;42;"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, null))

    session.write(osc("9;4;1;99999999999999999999"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, null))
  }

  /**
   * An OSC 9 that is not a well-formed progress report — a truncated `9;4`, an unknown state digit, another
   * ConEmu sub-command, or an iTerm2 desktop notification (the meaning of a plain OSC 9) — must leave the
   * current progress untouched rather than clearing it.
   */
  @Test
  fun malformedReportsLeaveProgressUntouched() = session(20, 3) { session ->
    val reported = TerminalProgress(TerminalProgressState.NORMAL, 25)
    session.write(osc("9;4;1;25"))
    assertThat(session.progress).isEqualTo(reported)

    session.write(osc("9;4"))              // no state at all
    session.write(osc("9;4;"))             // still no state
    session.write(osc("9;4;5"))            // unknown state digit
    session.write(osc("9;44;1"))           // not the `4` sub-command
    session.write(osc("9;2;A message"))    // ConEmu message box
    session.write(osc("9;Hello world"))    // iTerm2 desktop notification
    session.write(osc("4;1;#ff0000"))      // an unrelated OSC (palette set)
    assertThat(session.progress).isEqualTo(reported)
  }

  /** Both OSC terminators are accepted: BEL (0x07) and ST (`ESC \`). */
  @Test
  fun bothTerminators() = session(20, 3) { session ->
    session.write(osc("9;4;1;10", OscTerminator.BELL))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, 10))

    session.write(osc("9;4;1;20", OscTerminator.ST))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, 20))
  }

  /**
   * A PTY read can split a sequence at any byte boundary, so parsing must carry across
   * [TerminalEmulator.write] calls: fed one byte per write, the report must still arrive.
   */
  @Test
  fun reportSplitAcrossWrites() = session(20, 3) { session ->
    for (ch in osc("9;4;1;77")) {
      session.write(ch.toString())
    }
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, 77))
  }

  /**
   * The report is a pure side channel: the sequence itself must not reach the screen, and it must not
   * disturb the surrounding text or the cursor.
   */
  @Test
  fun reportPrintsNothing() = session(20, 3) { session ->
    session.write("before " + osc("9;4;1;50") + "after")
    session.assertScreenLines("before after")
    session.assertCursorPosition(13, 1)
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, 50))
  }

  /**
   * Progress tracking is independent of the OSC 1341 custom-command channel: unlike that one it needs no
   * listener registered, and the two must not interfere when they arrive in a single write.
   */
  @Test
  fun progressIsIndependentOfTheCustomCommandChannel() = session(20, 3) { session ->
    val received = ArrayList<List<String>>()
    session.write(osc("9;4;1;5"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, 5))

    session.customCommandListener = TerminalCustomCommandListener { received.add(it) }
    session.write(osc("1341;foo") + osc("9;4;1;15"))
    assertThat(session.progress).isEqualTo(TerminalProgress(TerminalProgressState.NORMAL, 15))
    assertThat(received).containsExactly(listOf("foo"))
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies the emulator's pull-based change tracking: [TerminalEmulator.takeChanges] must report a
 * change after a real change and nothing while idle, so a renderer can skip a full rebuild when the
 * screen did not change, and must report only the changed rows ([ScreenChange.Rows]) for an incremental
 * edit rather than the whole screen ([ScreenChange.All]).
 */
class ChangeTrackingTest {

  @Test
  fun reportsChangeOnWriteAndNoneWhenIdle() = session(20, 5) { session ->
    fun changed(): Boolean = session.takeChanges() != ScreenChange.None

    session.write("hello")
    assertThat(changed()).describedAs("a write must report a change").isTrue()
    assertThat(changed()).describedAs("nothing changed since the last poll -> None (the skip case)").isFalse()

    session.write("X")
    assertThat(changed()).describedAs("a subsequent write must report a change again").isTrue()
    assertThat(changed()).isFalse()

    session.resize(30, 8)
    assertThat(changed()).describedAs("a resize must report a change").isTrue()
    assertThat(changed()).isFalse()
  }

  @Test
  fun reportsOnlyTheEditedRow() = session(20, 5) { session ->
    session.write("hello")
    session.crlf()
    session.write("world")
    session.takeChanges() // consume the initial paint

    // Append one cell to the second row; only that row must be reported dirty, not the whole screen.
    session.write("!")
    assertThat(session.takeChanges()).isEqualTo(ScreenChange.Rows(intArrayOf(1)))
  }
}

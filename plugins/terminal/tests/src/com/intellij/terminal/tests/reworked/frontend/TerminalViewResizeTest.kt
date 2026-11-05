// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.terminal.tests.reworked.util.TerminalViewFixture
import com.intellij.terminal.tests.reworked.util.TerminalViewTestCase
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tests which panel resizes [TerminalViewImpl] reports to the session.
 *
 * Every case runs on both VT emulators, see [TerminalViewTestCase].
 */
internal class TerminalViewResizeTest(emulatorType: TerminalEmulatorType) : TerminalViewTestCase(emulatorType) {

  @Test
  fun `panel resize that keeps the grid sends no resize event`() = doTest { fixture ->
    fixture.resize(columns = 80, rows = 24)
    assertThat(fixture.awaitPtyResize()).isEqualTo(TermSize(80, 24))

    // The fixture grows the panel by one pixel, which keeps the grid, as a window drag does.
    fixture.awaitInputEventsHandled { fixture.resize(columns = 80, rows = 24) }
    assertThat(fixture.connector.awaitResize(0)).describedAs("the pty resize for an unchanged grid").isNull()

    fixture.resize(columns = 40, rows = 10)
    assertThat(fixture.awaitPtyResize()).isEqualTo(TermSize(40, 10))
  }

  /** The next size the session asks the pty for. The poll blocks, so it runs off the EDT. */
  private suspend fun TerminalViewFixture.awaitPtyResize(timeout: Duration = 5.seconds): TermSize? {
    return withContext(Dispatchers.IO) { connector.awaitResize(timeout.inWholeMilliseconds) }
  }
}

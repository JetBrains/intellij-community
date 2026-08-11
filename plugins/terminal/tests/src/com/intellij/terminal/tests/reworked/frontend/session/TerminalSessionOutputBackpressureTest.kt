// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.terminal.tests.reworked.util.TerminalOutputEventCollector
import com.intellij.terminal.tests.reworked.util.awaitEvent
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * The output flow's throughput contract, which both session pipelines share:
 *
 * - **Backpressure**: only a batch or so of [org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent]s
 *   may be pending at a time, so an event produced by a user action (Ctrl+C) reaches the UI right away instead
 *   of queueing behind buffered shell output. A short buffer is only safe because content events are
 *   incremental deltas: a session that kept reading the pty regardless would overwrite batches nobody has taken
 *   yet, and the collector would then be missing the lines those batches carried — permanently, since later
 *   deltas only describe the tail after them. So the session has to stop reading instead.
 * - **Coalescing**: projection into events runs on a fixed cadence rather than per PTY read, so a burst of
 *   output arrives as a few batches, not thousands.
 */
internal class TerminalSessionOutputBackpressureTest(emulatorType: TerminalEmulatorType) : TerminalSessionTestCase(emulatorType) {

  @Test
  fun `output produced before anything collects is not lost`() = runSessionTestWithoutCollector { session, connector, sessionScope ->
    // One feed per line, so each becomes its own read-loop iteration — far more input than the flow buffers,
    // every bit of it produced before the collector below exists.
    repeat(LINE_COUNT) { connector.feed("line$it\r\n") }
    // Ample time for a session that reads and emits regardless of collectors to drain all of the above and
    // keep only the last batch, which is the loss this test is here to catch.
    delay(DRAIN_WINDOW_MS.milliseconds)

    val collector = TerminalOutputEventCollector(session, sessionScope)

    // The trailing newline of the last line parks the cursor one logical line below it.
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.cursorLogicalLineIndex == LINE_COUNT.toLong() }

    assertThat(collector.documentLines())
      .describedAs("output produced before the collector subscribed was dropped, leaving holes in the document")
      .startsWith(*Array(LINE_COUNT) { "line$it" })
  }

  @Test
  fun `a burst of output is coalesced into few event batches`() = runSessionTest { _, connector, collector ->
    repeat(BURST_LINE_COUNT) { connector.feed("line$it\r\n") }

    collector.awaitEvent<TerminalContentUpdatedEvent> { it.cursorLogicalLineIndex == BURST_LINE_COUNT.toLong() }

    // The bound is deliberately loose: it fails only when coalescing is gone entirely (one batch per PTY
    // read), not when a slow machine stretches the burst across more projection ticks than usual.
    assertThat(collector.contentUpdates().size)
      .describedAs("a burst should be coalesced into few batches by the projection cadence")
      .isLessThan(BURST_LINE_COUNT / 5)
    // Coalescing must not cost completeness.
    assertThat(collector.documentLines()).startsWith(*Array(BURST_LINE_COUNT) { "line$it" })
  }
}

/** Well past anything the output flow buffers, and well within the default 80x24 session's scrollback. */
private const val LINE_COUNT: Int = 200

/** How long to let an unthrottled session drain the pty before concluding that this one did not. */
private const val DRAIN_WINDOW_MS: Int = 200

/**
 * Enough lines that per-read emission would visibly dwarf per-tick emission, while staying under both
 * emulators' history caps so scrollback eviction plays no part in this test.
 */
private const val BURST_LINE_COUNT: Int = 900

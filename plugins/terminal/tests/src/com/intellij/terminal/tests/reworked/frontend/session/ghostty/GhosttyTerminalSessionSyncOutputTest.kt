// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.terminal.tests.reworked.util.TerminalOutputEventCollector
import com.intellij.terminal.tests.reworked.util.awaitEvent
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.impl.TerminalBeepEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * DEC mode 2026 (synchronized output) in the Ghostty-backed
 * [com.intellij.terminal.frontend.session.ghostty.GhosttyTerminalSession].
 *
 * The mode is a presentation hint: the emulator keeps applying input to its grid throughout the block (see
 * `SynchronizedOutputTest` in the emulator module), so it is the *session* that must hold the frame back — it
 * stops projecting output events until the block closes, and then emits the finished frame in one go instead
 * of a half-drawn one. The risk that buys is a permanent freeze, since nothing forces a program to close its
 * block and no further output arrives to re-check the flag, so a watchdog forces a paint shortly after
 * `GhosttyTerminalSession.SYNC_OUTPUT_TIMEOUT` (1000 ms plus one projection tick). That timeout is a fixed
 * constant with no setting behind it, so these tests run against the production value: the two that need it
 * wait it out, and the one that must not trip it keeps its window short (see [EVENT_SETTLE_MS]).
 *
 * One behavior per test, each failing for a reason no other one catches:
 * [an open block emits nothing, then delivers everything at once] is the only one that sees an event of any
 * kind escaping an open block; [the watchdog paints a block the program never closes] is the only one that
 * needs the timer to fire at all; [expiring one block does not stop the next one from deferring] is the only
 * one that sees a watchdog paint disable the deferral of what follows.
 */
internal class GhosttyTerminalSessionSyncOutputTest : GhosttyTerminalSessionTestCase() {

  @Test
  fun `an open block emits nothing, then delivers everything at once`() = runSessionTest { _, connector, collector ->
    val mark = collector.currentEventCount()

    // Content plus a bell: the bell is a one-shot event queued by the emulator listener during the same write,
    // on a different path than content updates, so together they cover every kind of event the session emits.
    connector.feedInsideOpenBlock(BEGIN + "hidden" + BELL)

    collector.assertNothingEmittedSince(mark, "the session emitted while a synchronized-output block was open")

    connector.feed("shown$END")

    val frame = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("hidden") }
    assertThat(frame.text)
      .describedAs("the frame must be delivered whole, not split across the block boundary")
      .contains("hiddenshown")
    // Deferring a bell must not drop it.
    collector.awaitEvent<TerminalBeepEvent>()
  }

  @Test
  fun `the watchdog paints a block the program never closes`() = runSessionTest { _, connector, collector ->
    // A program that opens a block and then dies (or hangs) mid-frame: no closing sequence ever arrives, and
    // no further output will come to re-check the mode. Without the watchdog the view stays frozen for good
    // and this await times out.
    connector.feed(BEGIN + "stuck")
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("stuck") }

    // The block is still open, so later output is deferred again — but only until the re-armed watchdog
    // paints it; the view keeps refreshing at the watchdog cadence instead of freezing.
    connector.feed(" and moving")
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("stuck and moving") }
  }

  /**
   * The one thing the tests above cannot see: a watchdog paint must disable the deferral only until the next
   * deferred frame, not for good — content following it (a new block here, but the same applies within a
   * still-open one) must be held back again. Regression guard for the sticky "deferral expired" flag this
   * session once kept: the END/BEGIN boundary between two blocks can arrive in a single PTY chunk, where the
   * mode never reads as "off" in between, so the stale flag leaked into the second block and it streamed out
   * half-built.
   *
   * Deliberately uses the *whole-frame* check rather than [assertNothingEmittedSince] — no settle wait means
   * no race against the watchdog that is armed again here.
   */
  @Test
  fun `expiring one block does not stop the next one from deferring`() = runSessionTest { _, connector, collector ->
    connector.feed(BEGIN + "expired")
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("expired") } // the watchdog's paint
    connector.feed(END)

    connector.feedInsideOpenBlock(BEGIN + "next")
    connector.feed("block$END")

    val frame = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("next") }
    assertThat(frame.text)
      .describedAs("the watchdog paint disabled deferral for what followed, which streamed out half-built")
      .contains("nextblock")
  }

  /**
   * Feeds [text] (which must open a synchronized-output block) and returns once the emulator has consumed it,
   * so the caller's next feed is guaranteed to arrive while the block is already open rather than being parsed
   * together with it — without which the deferral tests would pass vacuously.
   *
   * Consumption is observed with a DSR query appended to [text]: emulator replies are not deferred by mode 2026,
   * and the session writes them back only after the emulator consumed the whole feed, so a reply proves the
   * block was open by then.
   */
  private fun LoopbackTtyConnector.feedInsideOpenBlock(text: String) {
    val replyArrived = CountDownLatch(1)
    responseHandler = { replyArrived.countDown() }
    try {
      feed(text + csi("6n"))
      assertThat(replyArrived.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        .describedAs("the emulator never consumed the opening half of the frame, so nothing was deferred " +
                     "and this test would prove nothing")
        .isTrue()
    }
    finally {
      responseHandler = null
    }
  }

  /**
   * Asserts the session has emitted no output event of any kind since [mark] (see [currentEventCount]).
   *
   * Call only after [feedInsideOpenBlock], which establishes that the read loop has already processed the
   * input this is asserting about. The settle wait then spans several projection ticks, so what it proves is
   * the deferral holding frames back — not merely the latency of a tick that has yet to run.
   */
  private suspend fun TerminalOutputEventCollector.assertNothingEmittedSince(mark: Int, message: String) {
    delay(EVENT_SETTLE_MS.milliseconds)
    assertThat(eventsSince(mark)).describedAs(message).isEmpty()
  }
}

/** DECSET 2026: begin a synchronized-output block. */
private val BEGIN: String = csi("?2026h")

/** DECRST 2026: end it. */
private val END: String = csi("?2026l")

/**
 * How long to let an emitted event reach the collector before concluding that none was emitted.
 *
 * Has to stay well under the session's own 1000 ms synchronized-output watchdog, or the watchdog's paint
 * lands inside the window and reads as a leak — while covering several of the session's 20 ms projection
 * ticks, so a frame that *would* be emitted has had every chance to be.
 */
private const val EVENT_SETTLE_MS: Int = 200

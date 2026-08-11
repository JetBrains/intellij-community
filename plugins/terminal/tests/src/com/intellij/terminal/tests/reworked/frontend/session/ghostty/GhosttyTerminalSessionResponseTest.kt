// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.jediterm.core.util.TermSize
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.session.impl.TerminalResizeEvent
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The Ghostty-backed [com.intellij.terminal.frontend.session.ghostty.GhosttyTerminalSession] answers host
 * queries (DSR, DA, OSC reports) through the emulator's write-pty effect, which fires *synchronously* inside
 * `emulator.write` — on the read thread, under the session lock, with ghostty still mid-parse.
 *
 * Writing the reply to the pty from there is therefore doubly wrong: libghostty-vt requires effects not to
 * block ("they are blocking further IO processing", `terminal.h`), and a pty whose buffer is full would park
 * the read thread while it still owns the lock, freezing resize and teardown along with it. The session must
 * queue replies and write them after releasing the lock.
 */
internal class GhosttyTerminalSessionResponseTest : GhosttyTerminalSessionTestCase() {

  @Test
  fun `a stalled reply write does not block resize`() = runSessionTest { session, connector, _ ->
    val replyStarted = CountDownLatch(1)
    val releaseReply = CountDownLatch(1)
    connector.responseHandler = {
      replyStarted.countDown()
      releaseReply.await() // a pty whose buffer nobody is draining
    }

    try {
      connector.feed(csi("6n")) // DSR: report cursor position; the emulator replies via the write-pty effect
      assertThat(replyStarted.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        .describedAs("the emulator never answered DSR, so nothing is being stalled and the test proves nothing")
        .isTrue()

      // The read thread is now parked inside the reply write. If it were still holding the session lock,
      // this resize could never be serviced.
      session.getInputChannel().send(TerminalResizeEvent(TerminalGridSize(columns = 100, rows = 30)))

      assertThat(connector.awaitResize(AWAIT_TIMEOUT_MS))
        .describedAs("resize never reached the pty: it is stuck behind the stalled reply write")
        .isEqualTo(TermSize(100, 30))
    }
    finally {
      // Always unpark the read thread, or teardown waits on it and the failure is reported as a timeout.
      releaseReply.countDown()
    }
  }

  @Test
  fun `replies still reach the pty`() = runSessionTest { _, connector, _ ->
    val replies = ArrayList<String>()
    val replyArrived = CountDownLatch(1)
    connector.responseHandler = { bytes ->
      synchronized(replies) { replies.add(String(bytes, Charsets.UTF_8)) }
      replyArrived.countDown()
    }

    connector.feed(csi("6n"))

    assertThat(replyArrived.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
      .describedAs("deferring the reply must not drop it")
      .isTrue()
    // Fresh 80x24 screen, cursor still home: CSI 1 ; 1 R.
    assertThat(synchronized(replies) { replies.toList() }).containsExactly(csi("1;1R"))
  }
}

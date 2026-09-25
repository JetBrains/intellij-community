// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The Ghostty-backed [com.intellij.terminal.frontend.session.ghostty.GhosttyTerminalSession] answers the `XTGETTCAP` query
 * for the terminal name `TN` (`DCS + q 544E ST`, with the key in hex) with the `TERM` of the started process.
 */
internal class GhosttyTerminalSessionTerminfoNameTest : GhosttyTerminalSessionTestCase() {

  @Test
  fun `the terminal name query reports TERM of the started process`() = runSessionTest(
    envVariables = mapOf("TERM" to "xterm-256color"),
  ) { _, connector, _ ->
    assertThat(connector.query(dcs("+q544E"))).isEqualTo(dcs("1+r544E=787465726D2D323536636F6C6F72"))
  }

  @Test
  fun `the terminal name query gets no reply without TERM`() = runSessionTest { _, connector, _ ->
    // The session replies in the order of the queries.
    // So when the first reply is the reply to the cursor position query (DSR), the name query got no reply.
    assertThat(connector.query(dcs("+q544E") + csi("6n"))).isEqualTo(csi("1;1R"))
  }

  /** Feeds [query] to the session and returns the first reply that the session writes to the pty. */
  private fun LoopbackTtyConnector.query(query: String): String? {
    val replies = LinkedBlockingQueue<String>()
    responseHandler = { bytes -> replies.add(String(bytes, Charsets.UTF_8)) }
    feed(query)
    return replies.poll(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
  }
}

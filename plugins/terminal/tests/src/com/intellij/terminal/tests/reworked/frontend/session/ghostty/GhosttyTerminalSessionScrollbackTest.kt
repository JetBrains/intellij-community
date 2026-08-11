// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.terminal.tests.reworked.util.awaitEvent
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.junit.Test

/**
 * Verifies that the Ghostty-backed [com.intellij.terminal.frontend.session.ghostty.GhosttyTerminalSession]
 * emits a *complete* incremental content stream even when far more lines scroll off than the scrollback can
 * hold.
 *
 * The session appends only the tail that changed since the last frame ([TerminalContentUpdatedEvent]); the count
 * of newly finalized history lines comes from a `HistoryMark`, which stays exact past the scrollback byte cap
 * where a raw `scrollbackRows` delta would plateau. Before the mark was wired in, a continuous scroll past the cap
 * silently dropped lines from the tail (and stalled the logical-line index). This test streams well past the cap
 * and reconstructs the document from the events, asserting every line survived, in order.
 */
internal class GhosttyTerminalSessionScrollbackTest : GhosttyTerminalSessionTestCase() {

  @Test
  fun `continuous scroll past the scrollback cap loses no lines`() = runSessionTest { _, connector, collector ->
    // The default terminal is 80x24 with ~1 MiB of scrollback, which holds only ~1000 rows at this width; 5000
    // lines therefore evict the oldest history several times over while the stream is still running.
    val total = 5_000
    connector.feed((0 until total).joinToString("\r\n") { "line$it" })

    // Wait until the whole stream has been processed: the cursor sits on the last logical line. (Before the fix
    // the logical index froze at the cap and never reached here.)
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.cursorLogicalLineIndex == (total - 1).toLong() }

    // Replay the incremental content events into a document and assert no line was dropped.
    val document = collector.documentLines()
    assertThat(document).hasSize(total)
    assertThat(document).isEqualTo((0 until total).map { "line$it" })
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.terminal.tests.reworked.util.awaitEvent
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.Osc8HyperlinkDto
import org.junit.Test

/**
 * OSC 8 hyperlinks through the Ghostty-backed session: the emulator tracks the link per cell, and
 * `TerminalEmulatorOutputProjector` must coalesce that into the [TerminalContentUpdatedEvent.osc8Hyperlinks]
 * ranges (offsets relative to the event text) the output model expects. The JediTerm counterpart is
 * [com.intellij.terminal.tests.reworked.frontend.TerminalOsc8HyperlinksEndToEndTest].
 */
internal class GhosttyTerminalSessionHyperlinkTest : GhosttyTerminalSessionTestCase() {

  @Test
  fun `linked text is reported as a hyperlink range`() = runSessionTest { _, connector, collector ->
    val uri = "https://example.com/foo"
    connector.feed("pre " + osc8(uri, "LINK") + " post END")

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("END") }
    val start = event.text.indexOf("LINK").toLong()
    assertThat(event.osc8Hyperlinks).containsExactly(Osc8HyperlinkDto(start, start + "LINK".length, uri))
  }

  @Test
  fun `a link spanning a soft-wrapped line stays one range`() = runSessionTest { _, connector, collector ->
    val uri = "https://example.com/wrapped"
    // 70 plain chars, then 20 linked chars: the link crosses the 80-column boundary into the next row.
    val linkText = "L".repeat(20)
    connector.feed("X".repeat(70) + osc8(uri, linkText) + "END")

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("END") }
    // Wrapped rows join without a '\n', so the linked text is contiguous in the event text.
    val start = event.text.indexOf(linkText).toLong()
    assertThat(start).isNotNegative()
    assertThat(event.osc8Hyperlinks).containsExactly(Osc8HyperlinkDto(start, start + linkText.length, uri))
  }

  @Test
  fun `unlinked text reports no hyperlinks`() = runSessionTest { _, connector, collector ->
    connector.feed("plain text END")

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("END") }
    assertThat(event.osc8Hyperlinks).isEmpty()
  }

  /** `ESC ] 8 ; ; <uri> ST <text> ESC ] 8 ; ; ST` — [text] hyperlinked to [uri]. */
  private fun osc8(uri: String, text: String): String {
    val st = Char(27) + "\\"
    return Char(27) + "]8;;" + uri + st + text + Char(27) + "]8;;" + st
  }
}

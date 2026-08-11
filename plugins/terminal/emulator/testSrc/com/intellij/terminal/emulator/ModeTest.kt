// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Terminal modes the UI must honor, read back through the [TerminalEmulator] mode getters: bracketed
 * paste (DEC 2004), the alternate-screen flag (DEC 1049), and mouse reporting protocol + encoding.
 * Alternate-screen *content* behavior (round trips, scrollback, cursor) is covered by
 * [AlternateScreenBufferTest].
 */
class ModeTest {

  @Test
  fun tracksBracketedPasteAndAlternateScreenModes() = session(20, 5) { session ->
    assertThat(session.bracketedPaste).isFalse()
    session.write(csi("?2004h"))
    assertThat(session.bracketedPaste).isTrue()

    assertThat(session.usingAlternateScreen).isFalse()
    session.write(csi("?1049h"))
    assertThat(session.usingAlternateScreen).isTrue()
    session.write(csi("?1049l"))
    assertThat(session.usingAlternateScreen).isFalse()
  }

  @Test
  fun tracksMouseProtocolAndEncoding() = session(20, 5) { session ->
    assertThat(session.mouseProtocol).isEqualTo(MouseProtocol.NONE)
    assertThat(session.mouseEncoding).isEqualTo(MouseEncoding.DEFAULT)

    session.write(csi("?1000h"))   // normal mouse tracking
    assertThat(session.mouseProtocol).isEqualTo(MouseProtocol.NORMAL)

    session.write(csi("?1006h"))   // SGR mouse encoding
    assertThat(session.mouseEncoding).isEqualTo(MouseEncoding.SGR)

    // Regression: SGR-pixels (DECSET 1016) must be reported as SGR_PIXELS, not collapsed to SGR.
    session.write(csi("?1016h"))
    assertThat(session.mouseEncoding).isEqualTo(MouseEncoding.SGR_PIXELS)
  }
}

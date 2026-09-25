// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.junit.jupiter.api.Test

/**
 * The color scheme query (`CSI ? 996 n`) and its reply: `CSI ? 997 ; 1 n` for dark, and `CSI ? 997 ; 2 n` for light.
 * The embedder sets the color scheme with [TerminalEmulator.setColorScheme].
 * While the program enables the color scheme reports (mode 2031), each call sends the same sequence as a report.
 */
class ColorSchemeTest {

  @Test
  fun queryGetsNoReplyBeforeEmbedderSetsColorScheme() = session(10, 3) { session ->
    session.write(csi("?996n"))
    session.assertResponses()
  }

  @Test
  fun queryReportsEmbedderColorScheme() = session(10, 3) { session ->
    session.setColorScheme(ColorScheme.DARK)
    session.write(csi("?996n"))

    session.setColorScheme(ColorScheme.LIGHT)
    session.write(csi("?996n"))

    session.assertResponses(csi("?997;1n"), csi("?997;2n"))
  }

  @Test
  fun reportIsSentOnlyWhileProgramEnablesIt() = session(10, 3) { session ->
    session.setColorScheme(ColorScheme.DARK)
    session.assertResponses()

    session.write(csi("?2031h"))
    session.setColorScheme(ColorScheme.LIGHT)
    // The same color scheme sends the report again: the embedder calls it for any change of its colors.
    session.setColorScheme(ColorScheme.LIGHT)
    session.assertResponses(csi("?997;2n"), csi("?997;2n"))

    session.write(csi("?2031l"))
    session.setColorScheme(ColorScheme.DARK)
    session.assertResponses(csi("?997;2n"), csi("?997;2n"))
  }

  @Test
  fun modeQueryReportsColorSchemeReportsAsSupported() = session(10, 3) { session ->
    session.write(csi($$"?2031$p"))  // DECRQM
    session.write(csi("?2031h"))
    session.write(csi($$"?2031$p"))
    session.assertResponses(csi($$"?2031;2$y"), csi($$"?2031;1$y"))  // 2 = reset, 1 = set
  }
}

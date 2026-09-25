// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.junit.jupiter.api.Test

/**
 * The color scheme query (`CSI ? 996 n`) and its reply: `CSI ? 997 ; 1 n` for dark, and `CSI ? 997 ; 2 n` for light.
 * The embedder sets the color scheme with [TerminalEmulator.setColorScheme].
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
}

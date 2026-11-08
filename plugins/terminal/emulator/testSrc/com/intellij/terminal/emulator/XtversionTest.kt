// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import com.intellij.terminal.emulator.impl.ghostty.GhosttyXtversion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The XTVERSION query (`CSI > q`) and its reply `DCS > | name ST`. Programs identify the terminal
 * by substrings of the name, so the reply must name JetBrains and must not name Ghostty.
 */
class XtversionTest {

  @Test
  fun queryReportsJetBrainsName() = session(10, 3) { session ->
    session.write(csi(">q"))
    session.assertResponses(dcs(">|JetBrains Terminal"))
  }

  @Test
  fun nameDoesNotClaimGhostty() {
    assertThat(GhosttyXtversion.NAME.lowercase()).doesNotContain("ghostty")
  }
}

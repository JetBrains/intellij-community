// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The `XTGETTCAP` query for the terminal name `TN` (`DCS + q 544E ST`, with the key in hex) and its reply
 * `DCS 1 + r 544E=<the hex name> ST`. The embedder sets the name with [TerminalEmulator.setTerminfoName].
 */
class TerminfoNameTest {

  @Test
  fun queryGetsNoReplyBeforeEmbedderSetsName() = session(10, 3) { session ->
    session.write(dcs("+q544E"))
    session.assertResponses()
  }

  @Test
  fun queryReportsEmbedderName() = session(10, 3) { session ->
    session.setTerminfoName("xterm-256color")
    session.write(dcs("+q544E"))
    session.assertResponses(dcs("1+r544E=787465726D2D323536636F6C6F72"))
  }

  @Test
  fun rejectsTooLongName() = session(10, 3) { session ->
    assertThatThrownBy { session.setTerminfoName("x".repeat(129)) }.isInstanceOf(IllegalArgumentException::class.java)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.junit.jupiter.api.Test

/**
 * The bell: a BEL (0x07) in the write stream must ring the bell (delivered via
 * [TerminalListener.onBell]) without printing anything or moving the cursor.
 */
class BellTest {

  @Test
  fun ringsBellOnBelCharacter() = session(20, 5) { session ->
    session.write("a" + BELL_CHAR + "b" + BELL_CHAR) // BEL (0x07) rings the bell without printing or moving
    session.assertScreenLines("ab")
    session.assertBellCount(2)
  }
}

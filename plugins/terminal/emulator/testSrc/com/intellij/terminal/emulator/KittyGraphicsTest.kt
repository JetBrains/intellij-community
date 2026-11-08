// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.junit.jupiter.api.Test

/**
 * The Kitty graphics protocol (`APC G ... ST`) is off while the IDE draws no images. A program detects
 * the protocol by a support query followed by DA1: an `OK` reply before the DA1 reply means support.
 * The emulator must stay silent on the query so that the program keeps its text fallback.
 */
class KittyGraphicsTest {

  private val da1Reply = csi("?62;22c")

  @Test
  fun supportQueryGetsNoReply() = session(10, 3) { session ->
    session.write(apc("Gi=31,s=1,v=1,a=q,t=d,f=24;AAAA"))
    session.write(csi("c"))
    session.assertResponses(da1Reply)
  }

  @Test
  fun transmissionIsIgnored() = session(10, 3) { session ->
    // Transmit and display a 1x1 RGB image. With the protocol on, the engine would reply `OK` and
    // move the cursor past the placement.
    session.write(apc("Ga=T,i=1,f=24,s=1,v=1;AAAA"))
    session.write(csi("c"))
    session.assertResponses(da1Reply)
    session.assertCursorPosition(1, 1)
  }
}

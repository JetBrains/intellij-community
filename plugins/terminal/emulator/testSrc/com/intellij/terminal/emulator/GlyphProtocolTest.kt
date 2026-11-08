// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.junit.jupiter.api.Test

/**
 * The Glyph protocol (`APC 25a1 ; verb ... ST`) is off while the IDE draws every cell with fonts.
 * A program detects the protocol by the support query `s` followed by DA1: any reply before the
 * DA1 reply means support, and a timeout means none. The emulator must stay silent on every verb
 * so that the program keeps its font glyphs.
 */
class GlyphProtocolTest {

  private val da1Reply = csi("?62;22c")

  @Test
  fun supportQueryGetsNoReply() = session(10, 3) { session ->
    session.write(apc("25a1;s"))
    session.write(csi("c"))
    session.assertResponses(da1Reply)
  }

  @Test
  fun registrationAndQueryGetNoReply() = session(10, 3) { session ->
    // With the protocol on, the engine would answer the registration with `status=0` and the
    // query with the coverage of the code point.
    session.write(apc("25a1;r;cp=e0a0;reply=1;AAAAAAAAAAAAAA=="))
    session.write(apc("25a1;q;cp=e0a0"))
    session.write(csi("c"))
    session.assertResponses(da1Reply)
  }
}

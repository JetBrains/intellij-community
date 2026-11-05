// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PrebuiltDevDistRunnerTest {
  @Test
  fun `each IDE of a split run gets the distribution of its product`() {
    val declared = listOf("idea", "ideaJetBrainsClient")

    assertEquals(0, declaredDevDistFor(declared, platformPrefix = "idea", baseIdePlatformPrefixForFrontend = null))
    assertEquals(1, declaredDevDistFor(declared, platformPrefix = "JetBrainsClient", baseIdePlatformPrefixForFrontend = "idea"))
  }

  @Test
  fun `a frontend without a declared distribution of its product gets none`() {
    assertNull(declaredDevDistFor(listOf("idea"), platformPrefix = "JetBrainsClient", baseIdePlatformPrefixForFrontend = "idea"))
    assertNull(declaredDevDistFor(listOf("idea", "RiderJetBrainsClient"), platformPrefix = "JetBrainsClient", baseIdePlatformPrefixForFrontend = "idea"))
  }
}

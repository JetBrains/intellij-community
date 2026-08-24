// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import org.jetbrains.idea.maven.execution.MavenExternalParameters.encodeProfiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MavenProfilesEncodingTest {
  private val profiles = linkedMapOf("enabled" to true, "disabled" to false)

  @Test
  fun `maven 3 gets plain profile names`() {
    assertEquals("enabled,!disabled", encodeProfiles(profiles, "3.9.9"))
    assertEquals("enabled,!disabled", encodeProfiles(profiles, null))
    assertEquals("enabled,!disabled", encodeProfiles(profiles))
  }

  @Test
  fun `maven 4 gets optional profile names`() {
    assertEquals("?enabled,!?disabled", encodeProfiles(profiles, "4.0.0"))
  }
}

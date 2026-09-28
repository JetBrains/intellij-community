// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MavenProjectChangesTest {
  @Test
  fun `test basics`() {
    assertTrue(MavenProjectChanges.ALL.hasChanges())
    assertTrue(MavenProjectChanges.ALL.hasDependencyChanges())
    assertTrue(MavenProjectChanges.ALL.hasPackagingChanges())

    assertFalse(MavenProjectChanges.NONE.hasChanges())
    assertFalse(MavenProjectChanges.NONE.hasDependencyChanges())
    assertFalse(MavenProjectChanges.NONE.hasPackagingChanges())
  }

  @Test
  fun `test builder`() {
    val builder = MavenProjectChangesBuilder()
    assertFalse(builder.hasChanges())

    builder.setHasSourceChanges(true)
    assertTrue(builder.hasChanges())
    assertTrue(builder.hasSourceChanges())

    builder.setAllChanges(false)
    assertFalse(builder.hasChanges())
    assertFalse(builder.hasSourceChanges())

    builder.setAllChanges(true)
    assertTrue(builder.hasChanges())
    assertTrue(builder.hasSourceChanges())
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenProjectChangesTest : UsefulTestCase() {
  @Test
  fun `test basics`() = runBlocking {
    TestCase.assertTrue(MavenProjectChanges.ALL.hasChanges())
    TestCase.assertTrue(MavenProjectChanges.ALL.hasDependencyChanges())
    TestCase.assertTrue(MavenProjectChanges.ALL.hasPackagingChanges())

    TestCase.assertFalse(MavenProjectChanges.NONE.hasChanges())
    TestCase.assertFalse(MavenProjectChanges.NONE.hasDependencyChanges())
    TestCase.assertFalse(MavenProjectChanges.NONE.hasPackagingChanges())

    TestCase.assertTrue(MavenProjectChanges.DEPENDENCIES.hasChanges())
    TestCase.assertTrue(MavenProjectChanges.DEPENDENCIES.hasDependencyChanges())
    TestCase.assertFalse(MavenProjectChanges.DEPENDENCIES.hasPackagingChanges())
  }

  @Test
  fun `test builder`() = runBlocking {
    val builder = MavenProjectChangesBuilder()
    assertFalse(builder.hasChanges())

    builder.setHasSourceChanges(true)
    TestCase.assertTrue(builder.hasChanges())
    TestCase.assertTrue(builder.hasSourceChanges())

    builder.setAllChanges(false)
    TestCase.assertFalse(builder.hasChanges())
    TestCase.assertFalse(builder.hasSourceChanges())

    builder.setAllChanges(true)
    TestCase.assertTrue(builder.hasChanges())
    TestCase.assertTrue(builder.hasSourceChanges())
  }
}
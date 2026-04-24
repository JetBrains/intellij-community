// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.run.configuration.MavenProfilesFiled
import org.junit.Test

class MavenProfilesFieldTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun testNoDuplicateProfiles() = runBlocking {
    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <profiles>
        <profile><id>myProfile</id></profile>
      </profiles>
    """.trimIndent())

    val field = MavenProfilesFiled(project, projectRoot, testRootDisposable)
    val profiles = field.getProfiles(project, projectRoot).toList()
    assertEquals("Profiles should not contain duplicates", profiles.distinct(), profiles)
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.run.configuration.MavenProfilesFiled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProfilesFieldTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testNoDuplicateProfiles() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <profiles>
        <profile><id>myProfile</id></profile>
      </profiles>
    """.trimIndent())

    val field = MavenProfilesFiled(maven.project, maven.projectRoot, maven.testRootDisposable)
    val profiles = field.getProfiles(maven.project, maven.projectRoot).toList()
    assertEquals(profiles.distinct(), profiles, "Profiles should not contain duplicates")
  }
}

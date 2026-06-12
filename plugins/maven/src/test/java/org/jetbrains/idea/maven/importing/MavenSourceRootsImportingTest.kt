// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.maven.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.assertModules
import org.jetbrains.idea.maven.fixtures.assertSources
import org.jetbrains.idea.maven.fixtures.assertTestSources
import org.jetbrains.idea.maven.fixtures.createProjectSubDirs
import org.jetbrains.idea.maven.fixtures.importProjectAsync
import org.jetbrains.idea.maven.fixtures.mavenImportingFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

internal @TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenSourceRootsImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @Test
  fun testKotlinSourceRootsAreImportedWithSmartDefaults() = runBlocking {
    maven.createProjectSubDirs("src/main/kotlin", "src/test/kotlin")

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-plugin</artifactId>
            <version>2.3.20</version>
            <extensions>true</extensions>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())

    maven.assertModules("project")
    maven.assertSources("project", "src/main/java", "src/main/kotlin")
    maven.assertTestSources("project", "src/test/java", "src/test/kotlin")
  }
}

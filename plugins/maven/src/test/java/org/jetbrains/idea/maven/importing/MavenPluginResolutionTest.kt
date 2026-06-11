// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.maven.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.importProjectAsync
import org.jetbrains.idea.maven.fixtures.mavenImportingFixture
import org.jetbrains.idea.maven.fixtures.projectsTree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import com.intellij.testFramework.UsefulTestCase.assertEmpty

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenPluginResolutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @Test
  fun `test resolve bundle packaging plugin versions`() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>bundle</packaging>
                    <build>
                      <plugins>
                        <plugin>
                          <extensions>true</extensions>
                          <groupId>org.apache.felix</groupId>
                          <artifactId>maven-bundle-plugin</artifactId>
                          <version>5.1.8</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    assertEquals(1, maven.projectsTree.projects.size)
    val errors = maven.projectsTree.projects.first().problems.filter { it.isError }
    assertEmpty(errors)
  }
}
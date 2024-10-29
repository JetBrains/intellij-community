// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenPluginResolutionTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun `test resolve bundle packaging plugin versions`() = runBlocking {
    importProjectAsync("""
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
    assertEquals(1, projectsTree.projects.size)
    assertEmpty(projectsTree.projects.first().problems)
  }
}
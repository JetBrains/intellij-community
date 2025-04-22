// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.FUCollectorTestCase.collectProjectStateCollectorEvents
import com.intellij.maven.testFramework.MavenImportingTestCase
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.indices.MavenMultiProjectImportTest
import org.junit.Test

class MavenLibrariesCollectorsTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun testShouldCollectInfoAboutLibs() = runBlocking {
    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <dependencies>
            <dependency>
              <groupId>junit</groupId>
              <artifactId>junit</artifactId>
              <version>4.11</version>
              <scope>test</scope>
            </dependency>
            
          </dependencies>
    """.trimIndent())

    val metrics = collectProjectStateCollectorEvents(
      MavenLibraryScopesCollector::class.java, project)

    val compiler = metrics.map { it.data.build() }.first {
      it["group_artifact_id"] == "junit:junit"
    }
    assertEquals("test", compiler["scope"])
  }
}
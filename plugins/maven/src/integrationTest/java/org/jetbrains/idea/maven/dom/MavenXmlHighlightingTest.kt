// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.configTest
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

@TestApplication
class MavenXmlHighlightingTest {
  private val maven by mavenDomFixture()

  @BeforeEach
  fun setUp() {
    MavenDistributionsCache.resolveEmbeddedMavenHome()
  }

  @Test
  fun testMavenValidation() {
    runBlocking {
      val text = Path.of(MavenCustomRepositoryHelper.originalTestDataPath, "MavenValidation.xml").readText()
      val file = maven.createProjectSubFile("MavenValidation.xml", text)
      maven.configTest(file)
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          maven.fixture.testHighlighting(false, false, false, file)
        }
      }
    }
  }
}

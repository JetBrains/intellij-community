// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.proofreading

import com.intellij.grazie.grazieTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class MavenGrazieSupportTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture)

  @Test
  fun `test no proofreading checks in maven-related files`() {
    grazie.runHighlightTestForFile("ide/language/maven/pom.xml")
    grazie.runHighlightTestForFile("ide/language/maven/library-1.0.0.pom")
    grazie.runHighlightTestForFile("ide/language/maven/_remote.repositories")
    grazie.runHighlightTestForFile("ide/language/maven/library-1.0.0.pom.sha1")
  }
}
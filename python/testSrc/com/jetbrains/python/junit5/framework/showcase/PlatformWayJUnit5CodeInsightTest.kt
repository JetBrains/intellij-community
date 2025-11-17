// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.showcase

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.PythonMockSdk
import com.jetbrains.python.junit5.framework.pyMockSdkFixture
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * This test represents the platform way of writing JUnit5 tests.
 *
 * Unlike [PyJUnit5CodeInsightExampleTest],
 * all fixtures here are defined explicitly.
 */
@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/python/testData/junit5/showcase/PyTypeCheckerInspection")
class PlatformWayJUnit5CodeInsightTest {
  companion object {
    private val tempDir = tempPathFixture()
    private val project = projectFixture(tempDir, openAfterCreation = true)
    @Suppress("unused")
    private val module = project.moduleFixture(tempDir, addPathToSourceRoot = true)
    @Suppress("unused")
    private val mockSdk = project.pyMockSdkFixture(module) {
      PythonMockSdk.create(LanguageLevel.getLatest())
    }
  }
  private val codeInsightFixture by codeInsightFixture(project, tempDir)

  @BeforeEach
  fun beforeEach() {
    codeInsightFixture.enableInspections(PyTypeCheckerInspection::class.java)
  }

  @Test
  fun testAssignIntToStr() {
    codeInsightFixture.configureByText("test.py",
                       "x: str = <warning descr=\"Expected type 'str', got 'int' instead\">3</warning>")
    codeInsightFixture.checkHighlighting()
  }

  @Test
  fun testSingle() {
    codeInsightFixture.configureByFile("single.py")
    codeInsightFixture.checkHighlighting()
  }
}
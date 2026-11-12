// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestUtil.ProviderTestMode
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestUtil.doTestProvider
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.junit5.framework.pyMockInterpreterFixture
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import org.junit.jupiter.api.Test

@TestApplication
@Subsystems.CodeInsight
@Components.InlayHints
@Layers.Functional
class PyPseudoKeywordPatternInlayHintsProviderTest {
  companion object {
    private val tempDir = tempPathFixture()
    private val projectFixture = projectFixture(tempDir, openAfterCreation = true)
    private val pyProject = projectFixture.pyProjectFixture(tempDir)

    @Suppress("unused")
    private val interpreter = projectFixture.pyMockInterpreterFixture(pyProject) { PythonMockSdk.create(LanguageLevel.getLatest()) }
  }

  private val myFixture by codeInsightFixture(projectFixture, tempDir)

  @Test
  fun testSimplePositional() {
    doTest(
      """
class Point:
    __match_args__ = ("x", "y")
    def __init__(self, x, y):
        self.x = x
        self.y = y

def f(p: Point):
    match p:
        case Point(/*<# x= #>*/1, /*<# y= #>*/b):
            pass
      """
    )
  }

  @Test
  fun testStopsBeforeKeyword() {
    doTest(
      """
class Point:
    __match_args__ = ("x", "y")

def f(p: Point):
    match p:
        case Point(/*<# x= #>*/1, y=2):  # second is keyword, no inlay expected there
            pass
      """
    )
  }

  private fun doTest(text: String) {
    doTestProvider(myFixture, 
      "A.py",
      text,
      PyPseudoKeywordPatternInlayHintsProvider(),
      emptyMap(),
      verifyHintsPresence = true,
      testMode = ProviderTestMode.SIMPLE
    )
  }
}

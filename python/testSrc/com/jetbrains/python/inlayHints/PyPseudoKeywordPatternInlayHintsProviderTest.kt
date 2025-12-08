// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.psi.LanguageLevel

class PyPseudoKeywordPatternInlayHintsProviderTest : DeclarativeInlayHintsProviderTestCase() {
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
    doTestProvider(
      "A.py",
      text,
      PyPseudoKeywordPatternInlayHintsProvider(),
      emptyMap(),
      verifyHintsPresence = true,
      testMode = ProviderTestMode.SIMPLE
    )
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return PyLightProjectDescriptor(LanguageLevel.getLatest())
  }
}

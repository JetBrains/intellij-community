// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.idea.TestFor
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestUtil.ProviderTestMode
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestUtil.doTestProvider
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.junit5.framework.pyMockInterpreterFixture
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import org.junit.jupiter.api.Test

@TestApplication
@TestFor(issues = ["PY-85836"])
@Subsystems.CodeInsight
@Layers.Functional
class PyEnumAutoValueInlayHintsProviderTest {
  companion object {
    private val tempDir = tempPathFixture()
    private val projectFixture = projectFixture(tempDir, openAfterCreation = true)
    private val pyProject = projectFixture.pyProjectFixture(tempDir)

    @Suppress("unused")
    private val interpreter = projectFixture.pyMockInterpreterFixture(pyProject) { PythonMockSdk.create(LanguageLevel.getLatest()) }
  }

  private val myFixture by codeInsightFixture(projectFixture, tempDir)

  @Test
  fun `test consecutive auto values`(): Unit = doTest("""
    from enum import Enum, auto
    class E(Enum):
        A = auto()/*<#  = 1 #>*/
        B = auto()/*<#  = 2 #>*/
        C = auto()/*<#  = 3 #>*/
  """)

  @Test
  fun `test qualified auto`(): Unit = doTest("""
    import enum
    class E(enum.Enum):
        A = enum.auto()/*<#  = 1 #>*/
        B = enum.auto()/*<#  = 2 #>*/
  """)

  @Test
  fun `test int enum`(): Unit = doTest("""
    from enum import IntEnum, auto
    class E(IntEnum):
        A = auto()/*<#  = 1 #>*/
        B = auto()/*<#  = 2 #>*/
  """)

  @Test
  fun `test auto continues from explicit int value`(): Unit = doTest("""
    from enum import Enum, auto
    class E(Enum):
        A = auto()/*<#  = 1 #>*/
        B = 5
        C = auto()/*<#  = 6 #>*/
        D = auto()/*<#  = 7 #>*/
  """)

  @Test
  fun `test auto skips non-int explicit value`(): Unit = doTest("""
    from enum import Enum, auto
    class E(Enum):
        A = auto()/*<#  = 1 #>*/
        B = "x"
        C = auto()/*<#  = 2 #>*/
  """)

  @Test
  fun `test flag uses powers of two`(): Unit = doTest("""
    from enum import Flag, auto
    class E(Flag):
        A = auto()/*<#  = 1 #>*/
        B = auto()/*<#  = 2 #>*/
        C = auto()/*<#  = 4 #>*/
        D = auto()/*<#  = 8 #>*/
  """)

  @Test
  fun `test int flag uses powers of two`(): Unit = doTest("""
    from enum import IntFlag, auto
    class E(IntFlag):
        READ = auto()/*<#  = 1 #>*/
        WRITE = auto()/*<#  = 2 #>*/
        EXECUTE = auto()/*<#  = 4 #>*/
  """)

  @Test
  fun `test str enum uses lower-cased name`(): Unit = doTest("""
    from enum import StrEnum, auto
    class Color(StrEnum):
        RED = auto()/*<#  = 'red' #>*/
        Green = auto()/*<#  = 'green' #>*/
  """)

  @Test
  fun `test no hint for custom generate next value`(): Unit = doTest("""
    from enum import Enum, auto
    class E(Enum):
        @staticmethod
        def _generate_next_value_(name, start, count, last_values):
            return name
        A = auto()
        B = auto()
  """, verifyHintsPresence = false)

  @Test
  fun `test no hint after unevaluable value`(): Unit = doTest("""
    from enum import Enum, auto
    def factory(): ...
    class E(Enum):
        A = factory()
        B = auto()
  """, verifyHintsPresence = false)

  @Test
  fun `test no hint outside enum`(): Unit = doTest("""
    from enum import auto
    x = auto()
  """, verifyHintsPresence = false)

  private fun doTest(text: String, verifyHintsPresence: Boolean = true) {
    doTestProvider(myFixture, 
      "test.py",
      text.trimIndent(),
      PyEnumAutoValueInlayHintsProvider(),
      emptyMap(),
      verifyHintsPresence = verifyHintsPresence,
      testMode = ProviderTestMode.SIMPLE
    )
  }
}

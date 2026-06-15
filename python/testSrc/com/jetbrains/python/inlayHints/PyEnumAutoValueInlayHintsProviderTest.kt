// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.idea.TestFor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.psi.LanguageLevel

@TestFor(issues = ["PY-85836"])
class PyEnumAutoValueInlayHintsProviderTest : DeclarativeInlayHintsProviderTestCase() {

  fun `test consecutive auto values`() = doTest("""
    from enum import Enum, auto
    class E(Enum):
        A = auto()/*<#  = 1 #>*/
        B = auto()/*<#  = 2 #>*/
        C = auto()/*<#  = 3 #>*/
  """)

  fun `test qualified auto`() = doTest("""
    import enum
    class E(enum.Enum):
        A = enum.auto()/*<#  = 1 #>*/
        B = enum.auto()/*<#  = 2 #>*/
  """)

  fun `test int enum`() = doTest("""
    from enum import IntEnum, auto
    class E(IntEnum):
        A = auto()/*<#  = 1 #>*/
        B = auto()/*<#  = 2 #>*/
  """)

  fun `test auto continues from explicit int value`() = doTest("""
    from enum import Enum, auto
    class E(Enum):
        A = auto()/*<#  = 1 #>*/
        B = 5
        C = auto()/*<#  = 6 #>*/
        D = auto()/*<#  = 7 #>*/
  """)

  fun `test auto skips non-int explicit value`() = doTest("""
    from enum import Enum, auto
    class E(Enum):
        A = auto()/*<#  = 1 #>*/
        B = "x"
        C = auto()/*<#  = 2 #>*/
  """)

  fun `test flag uses powers of two`() = doTest("""
    from enum import Flag, auto
    class E(Flag):
        A = auto()/*<#  = 1 #>*/
        B = auto()/*<#  = 2 #>*/
        C = auto()/*<#  = 4 #>*/
        D = auto()/*<#  = 8 #>*/
  """)

  fun `test int flag uses powers of two`() = doTest("""
    from enum import IntFlag, auto
    class E(IntFlag):
        READ = auto()/*<#  = 1 #>*/
        WRITE = auto()/*<#  = 2 #>*/
        EXECUTE = auto()/*<#  = 4 #>*/
  """)

  fun `test str enum uses lower-cased name`() = doTest("""
    from enum import StrEnum, auto
    class Color(StrEnum):
        RED = auto()/*<#  = 'red' #>*/
        Green = auto()/*<#  = 'green' #>*/
  """)

  fun `test no hint for custom generate next value`() = doTest("""
    from enum import Enum, auto
    class E(Enum):
        @staticmethod
        def _generate_next_value_(name, start, count, last_values):
            return name
        A = auto()
        B = auto()
  """, verifyHintsPresence = false)

  fun `test no hint after unevaluable value`() = doTest("""
    from enum import Enum, auto
    def factory(): ...
    class E(Enum):
        A = factory()
        B = auto()
  """, verifyHintsPresence = false)

  fun `test no hint outside enum`() = doTest("""
    from enum import auto
    x = auto()
  """, verifyHintsPresence = false)

  private fun doTest(text: String, verifyHintsPresence: Boolean = true) {
    doTestProvider(
      "test.py",
      text.trimIndent(),
      PyEnumAutoValueInlayHintsProvider(),
      emptyMap(),
      verifyHintsPresence = verifyHintsPresence,
      testMode = ProviderTestMode.SIMPLE
    )
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return PyLightProjectDescriptor(LanguageLevel.getLatest())
  }
}

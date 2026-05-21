// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.psi.LanguageLevel

class PyNumericPromotionInlayHintsProviderTest : DeclarativeInlayHintsProviderTestCase() {

  fun `test float shows int promotion`() = doTest("""
    a: float/*<#  | int #>*/
  """)

  fun `test complex shows float and int promotion`() = doTest("""
    a: complex/*<#  | float | int #>*/
  """)

  fun `test float with int already present shows no hint`() = doTest("""
    a: float | int
  """, verifyHintsPresence = false)

  fun `test complex with float and int already present shows no hint`() = doTest("""
    a: complex | float | int
  """, verifyHintsPresence = false)

  fun `test complex with only int shows float promotion`() = doTest("""
    a: complex/*<#  | float #>*/ | int
  """)

  fun `test complex with only float shows int promotion`() = doTest("""
    a: complex/*<#  | int #>*/ | float/*<#  | int #>*/
  """)

  fun `test float in union shows int promotion`() = doTest("""
    a: str | float/*<#  | int #>*/
  """)

  fun `test int shows no hint`() = doTest("""
    a: int
  """, verifyHintsPresence = false)

  fun `test str shows no hint`() = doTest("""
    a: str
  """, verifyHintsPresence = false)

  fun `test typing Union float shows int promotion`() = doTest("""
    from typing import Union
    a: Union[str, float/*<#  | int #>*/]
  """)

  fun `test typing Union complex shows float and int promotion`() = doTest("""
    from typing import Union
    a: Union[str, complex/*<#  | float | int #>*/]
  """)

  fun `test typing Union float with int shows no hint`() = doTest("""
    from typing import Union
    a: Union[float, int]
  """, verifyHintsPresence = false)

  fun `test function parameter annotation`() = doTest("""
    def foo(x: float/*<#  | int #>*/):
        pass
  """)

  fun `test function return annotation`() = doTest("""
    def foo() -> float/*<#  | int #>*/:
        pass
  """)

  fun `test class attribute annotation`() = doTest("""
    class Foo:
        x: float/*<#  | int #>*/
  """)

  fun `test nested`() = doTest("""
    x: list[float/*<#  | int #>*/]
  """)

  fun `test value position`() = doTest("""
    x = list[float/*<#  | int #>*/]()
    
    # not type positions
    print(float)
    [float][float]
  """)

  fun `test type variable declaration`() = doTest("""
    class A[T: float/*<#  | int #>*/ = float/*<#  | int #>*/]: ...
  """)

  fun `test type alias`() = doTest("""
    type T = float/*<#  | int #>*/
  """)

  fun `test type list`() = doTest("""
    from collections.abc import Callable
    
    a: Callable[[float/*<#  | int #>*/, int], int]
  """)

  fun `test subscription with multiple types`() = doTest("""
    from collections.abc import Callable
    
    a: dict[float/*<#  | int #>*/, int]
  """)

  // TODO: support when `TypeForm` is supported PY-89043
  fun `test type expression`() = doTest("""
    from typing import cast

    cast(float, 1)
  """, verifyHintsPresence = false)

  private fun doTest(text: String, verifyHintsPresence: Boolean = true) {
    doTestProvider(
      "test.py",
      text.trimIndent(),
      PyNumericPromotionInlayHintsProvider(),
      emptyMap(),
      verifyHintsPresence = verifyHintsPresence,
      testMode = ProviderTestMode.SIMPLE
    )
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return PyLightProjectDescriptor(LanguageLevel.getLatest())
  }
}

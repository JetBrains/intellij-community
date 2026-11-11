// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
class PyNumericPromotionInlayHintsProviderTest {
  companion object {
    private val tempDir = tempPathFixture()
    private val projectFixture = projectFixture(tempDir, openAfterCreation = true)
    private val pyProject = projectFixture.pyProjectFixture(tempDir)

    @Suppress("unused")
    private val interpreter = projectFixture.pyMockInterpreterFixture(pyProject) { PythonMockSdk.create(LanguageLevel.getLatest()) }
  }

  private val myFixture by codeInsightFixture(projectFixture, tempDir)

  @Test
  fun `test float shows int promotion`(): Unit = doTest("""
    a: float/*<#  | int #>*/
  """)

  @Test
  fun `test complex shows float and int promotion`(): Unit = doTest("""
    a: complex/*<#  | float | int #>*/
  """)

  @Test
  fun `test float with int already present shows no hint`(): Unit = doTest("""
    a: float | int
  """, verifyHintsPresence = false)

  @Test
  fun `test complex with float and int already present shows no hint`(): Unit = doTest("""
    a: complex | float | int
  """, verifyHintsPresence = false)

  @Test
  fun `test complex with only int shows float promotion`(): Unit = doTest("""
    a: complex/*<#  | float #>*/ | int
  """)

  @Test
  fun `test complex with only float shows int promotion`(): Unit = doTest("""
    a: complex/*<#  | int #>*/ | float/*<#  | int #>*/
  """)

  @Test
  fun `test float in union shows int promotion`(): Unit = doTest("""
    a: str | float/*<#  | int #>*/
  """)

  @Test
  fun `test int shows no hint`(): Unit = doTest("""
    a: int
  """, verifyHintsPresence = false)

  @Test
  fun `test str shows no hint`(): Unit = doTest("""
    a: str
  """, verifyHintsPresence = false)

  @Test
  fun `test typing Union float shows int promotion`(): Unit = doTest("""
    from typing import Union
    a: Union[str, float/*<#  | int #>*/]
  """)

  @Test
  fun `test typing Union complex shows float and int promotion`(): Unit = doTest("""
    from typing import Union
    a: Union[str, complex/*<#  | float | int #>*/]
  """)

  @Test
  fun `test typing Union float with int shows no hint`(): Unit = doTest("""
    from typing import Union
    a: Union[float, int]
  """, verifyHintsPresence = false)

  @Test
  fun `test function parameter annotation`(): Unit = doTest("""
    def foo(x: float/*<#  | int #>*/):
        pass
  """)

  @Test
  fun `test function return annotation`(): Unit = doTest("""
    def foo() -> float/*<#  | int #>*/:
        pass
  """)

  @Test
  fun `test class attribute annotation`(): Unit = doTest("""
    class Foo:
        x: float/*<#  | int #>*/
  """)

  @Test
  fun `test nested`(): Unit = doTest("""
    x: list[float/*<#  | int #>*/]
  """)

  @Test
  fun `test value position`(): Unit = doTest("""
    x = list[float/*<#  | int #>*/]()
    
    # not type positions
    print(float)
    [float][float]
  """)

  @Test
  fun `test type variable declaration`(): Unit = doTest("""
    class A[T: float/*<#  | int #>*/ = float/*<#  | int #>*/]: ...
  """)

  @Test
  fun `test type alias`(): Unit = doTest("""
    type T = float/*<#  | int #>*/
  """)

  @Test
  fun `test type list`(): Unit = doTest("""
    from collections.abc import Callable
    
    a: Callable[[float/*<#  | int #>*/, int], int]
  """)

  @Test
  fun `test subscription with multiple types`(): Unit = doTest("""
    from collections.abc import Callable
    
    a: dict[float/*<#  | int #>*/, int]
  """)

  // TODO: support when `TypeForm` is supported PY-89043
  @Test
  fun `test type expression`(): Unit = doTest("""
    from typing import cast

    cast(float, 1)
  """, verifyHintsPresence = false)

  private fun doTest(text: String, verifyHintsPresence: Boolean = true) {
    doTestProvider(myFixture, 
      "test.py",
      text.trimIndent(),
      PyNumericPromotionInlayHintsProvider(),
      emptyMap(),
      verifyHintsPresence = verifyHintsPresence,
      testMode = ProviderTestMode.SIMPLE
    )
  }
}

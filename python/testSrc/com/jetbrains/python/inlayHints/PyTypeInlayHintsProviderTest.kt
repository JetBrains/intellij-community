// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.psi.LanguageLevel

class PyTypeInlayHintsProviderTest : DeclarativeInlayHintsProviderTestCase() {
  fun testRevealType() {
    doTest("""
from typing import reveal_type

def foo(a: int) -> str:
    reveal_type(a)/*<# int #>*/
    return "Hi!"

reveal_type(foo(1))/*<# str #>*/
    """.trimIndent())
  }
  
  fun testFunctionReturnType() {
    doTest("""
def foo(a: int) -> str: # no inlay here
    return "Hi!"

def bar(a: int)/*<# -> str #>*/:
    return "Hi!"
    
def gen(a: int)/*<# -> Generator[int | str | float, Any, str | int] #>*/:
    yield 42
    yield "str"
    yield 42.5
    if a > 0:
        return "Hi!"
    return 42
    """.trimIndent())
  }
  
  fun testPreview() {
    doTest("""    
from typing import reveal_type

def example(x: int, y: float)/*<# -> float #>*/:
    reveal_type(x + y)/*<# float #>*/
    return x + y

reveal_type(example(1, 2.5))/*<# float #>*/
    """.trimIndent())
  }

  private val allOptions = mapOf(
    PyTypeInlayHintsProvider.REVEAL_TYPE_OPTION_ID to true,
    PyTypeInlayHintsProvider.FUNCTION_RETURN_TYPE_OPTION_ID to true,
  )

  private fun doTest(text: String) {
    doTestProvider("A.py", text, PyTypeInlayHintsProvider(), allOptions, verifyHintsPresence = true, testMode = ProviderTestMode.SIMPLE)
  }

  override fun getProjectDescriptor(): LightProjectDescriptor? {
    return PyLightProjectDescriptor(LanguageLevel.getLatest())
  }
}
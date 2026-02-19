// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.codeInsight.hints.Option
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyTestCase

class PyInlayParameterHintsTest : PyTestCase() {

  fun testHintsShownForFunctionCallWithMultipleArguments() {
    doTest("""
      def foo(a, b, c):
          pass
      
      foo(<hint text="a:"/>1, <hint text="b:"/>2, <hint text="c:"/>3)
    """.trimIndent())
  }

  fun testHintsNotShownForFunctionCallWithSingleArgument() {
    doTest("""
      def foo(a):
          pass
      
      foo(1)
    """.trimIndent())
  }

  fun testHintsNotShownForKeywordArguments() {
    doTest("""
      def foo(a, b):
          pass
      
      foo(<hint text="a:"/>1, b=2)
    """.trimIndent())
  }

  fun testHintsShownForClassConstructorCall() {
    doTestWithOptions("""
      class Clazz:
          def __init__(self, a, b, c):
              pass
          
      c = Clazz(<hint text="a:"/>1, <hint text="b:"/>2, <hint text="c:"/>3)
    """.trimIndent(),
    PythonInlayParameterHintsProvider.showForClassConstructorCalls)
  }

  fun testHintsShownOnlyForLiterals() {
    doTestWithOptions("""
      def foo(a, b):
          pass
          
      x = "variable"
      foo(<hint text="a:"/>"literal", x)
    """.trimIndent(),
    PythonInlayParameterHintsProvider.showForNonLiteralArguments,
    enabled = false)
  }

  fun testHintsForNonKeywordArguments() {
    doTest("""
      def foo(*args):
          pass
     
     foo(<hint text="*args:"/>1, 2, 3, 4, 5)
    """.trimIndent())
  }

  fun testHintsForBuiltinFunctionCallsNotShownByDefault() {
    doTest("""
      class Clazz:
          pass
          
      c = Clazz
      isinstance(c, Clazz)
    """.trimIndent())
  }

  fun testHintsForPositionalArgumentAndKwargs() {
    doTest("""
      def fun(arg, **kwargs):
          pass
      
      fun(<hint text="arg:"/>1, a=2, b=3, c=4)
    """.trimIndent())
  }

  fun testsHintsNotShownForOverloads() {
    doTest("""
      from typing import overload, Any
      
      @overload
      def bar(a: int, b: int) -> None:
          pass

      @overload
      def bar(c: str, d: str) -> None:
          pass

      def bar(*args: Any, **kwargs: Any) -> None:
          pass

      bar(1, 2)
    """.trimIndent())
  }

  fun testsHintsShownForOverloadsWithSameSignatures() {
    doTest("""
      from typing import overload, Any


      @overload
      def bar(a: int, b: int) -> None:
          pass


      @overload
      def bar(a: str, b: str) -> None:
          pass


      def bar(*args: Any, **kwargs: Any) -> None:
          pass


      bar(<hint text="a:"/>1, <hint text="b:"/>2)
    """.trimIndent())
  }

  fun testsHintsNotShownForOverloadsWithDifferentSignatures() {
    doTest("""
      from typing import overload, Any


      @overload
      def bar(c: int, d: int, e: float) -> None:
          pass


      @overload
      def bar(a: str, b: str) -> None:
          pass


      def bar(*args: Any, **kwargs: Any) -> None:
          pass


      bar(1, 2)
    """.trimIndent())
  }

  fun testHintsShownForLiteralCollectionArguments() {
    doTestWithOptions("""
      def foo(a, b, c, d):
          pass

      a = 1

      foo(a, <hint text="b:"/>[1, 2], <hint text="c:"/>(3, 4), <hint text="d:"/>{"5": 5, "6": 6})
    """.trimIndent(),
    PythonInlayParameterHintsProvider.showForNonLiteralArguments,
    enabled = false)
  }

  private fun doTest(text: String) {
    enableAllHints()
    myFixture.configureByText(PythonFileType.INSTANCE, text)
    myFixture.testInlays()
  }

  private fun doTestWithOptions(text: String, vararg options: Option, enabled: Boolean = true) {
    options.forEach { option->  option.set(enabled) }
    myFixture.configureByText(PythonFileType.INSTANCE, text)
    myFixture.testInlays()
  }

  private fun enableAllHints() {
    PythonInlayParameterHintsProvider().supportedOptions.forEach { option: Option ->
      option.set(true)
    }
  }
}
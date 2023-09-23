// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.actionSystem.IdeActions
import com.jetbrains.python.fixtures.PyTestCase

class PyMoveElementLeftRightTest : PyTestCase() {

  fun testListElement() {
    doTest("""
      a = [1<caret>, 2, 3]
    """.trimIndent(), """
      a = [2, 1, 3]
    """.trimIndent())
  }

  fun testTupleListElement() {
    doTest("""
      a = 1<caret>, 2, 3
    """.trimIndent(), """
      a = 2, 1, 3
    """.trimIndent())
  }

  fun testDictElement() {
    doTest("""
      a = {"a":<caret> 1, "b": 2, "c": 3}
    """.trimIndent(), """
      a = {"b": 2, "a": 1, "c": 3}
    """.trimIndent())
  }

  fun testFuncParameter() {
    doTest("""
      def foo(a<caret>, b, c):
          pass
    """.trimIndent(), """
      def foo(b, a, c):
          pass
    """.trimIndent())
  }

  fun testFuncParameterWithTypeAnnotation() {
    doTest("""
      def foo(a<caret>: int, b: str, c: float):
          pass
    """.trimIndent(), """
      def foo(b: str, a: int, c: float):
          pass
    """.trimIndent())
  }

  fun testFuncArgument() {
    doTest("""
      foo("a<caret>", "b", "c")
    """.trimIndent(), """
      foo("b", "a", "c")
    """.trimIndent())
  }

  fun testNamedFuncArgument() {
    doTest("""
      foo(a=<caret>"a", b="b", c="c")
    """.trimIndent(), """
      foo(b="b", a="a", c="c")
    """.trimIndent())
  }

  fun testFromImportStatementElement() {
    doTest("""
      from typing import List<caret>, Dict, Tuple
    """.trimIndent(), """
      from typing import Dict, List, Tuple
    """.trimIndent())
  }

  fun testImportStatementElement() {
    doTest("""
      import typing<caret>, datetime, sys
    """.trimIndent(), """
      import datetime, typing, sys
    """.trimIndent())
  }

  fun testMappingPatternElement() {
    doTest("""
      match x:
        case {"a"<caret>: 1, "b": 2, "c": 3}:
          pass
    """.trimIndent(), """
      match x:
        case {"b": 2, "a": 1, "c": 3}:
          pass
    """.trimIndent())
  }

  fun testPatternArgumentListElement() {
    doTest("""
      match x:
        case Class(<caret>foo=42, bar=str()):
          pass
    """.trimIndent(), """
      match x:
        case Class(bar=str(), foo=42):
          pass
    """.trimIndent())
  }

  fun testWithStatementElement() {
    doTest("""
      with A()<caret> as a, B() as b, C() as c:
        pass
    """.trimIndent(), """
      with B() as b, A() as a, C() as c:
        pass
    """.trimIndent())
  }

  fun testNonlocalStatementElement() {
    doTest("""
      def foo():
        def bar():
          nonlocal a<caret>, b, c
          pass
        pass
    """.trimIndent(), """
      def foo():
        def bar():
          nonlocal b, a, c
          pass
        pass
    """.trimIndent())
  }

  fun testDelStatementElement() {
    doTest("""
      del a<caret>, b, c
    """.trimIndent(), """
      del b, a, c
    """.trimIndent())
  }
  
  private fun doTest(text: String, expected: String) {
    myFixture.configureByText(PythonFileType.INSTANCE, text)
    myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_RIGHT)
    myFixture.checkResult(expected)
  }
}
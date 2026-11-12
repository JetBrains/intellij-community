// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.idea.TestFor
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-91546"], classes = [PyCodeBlockProvider::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyCodeBlockProviderTest : PyCodeInsightTestCase() {

  @Test
  fun `caret inside a simple statement selects the enclosing block statement`() = assertCodeBlock("""
    def f():
        x = 1
    #       └ CARET
        y = 2
  """, """
    def f():
        x = 1
        y = 2
  """)

  @Test
  fun `caret on a block statement keyword selects that statement`() = assertCodeBlock("""
    def f():
        if a:
    #      └ CARET
            g()
        h()
  """, """
    if a:
            g()
  """)

  @Test
  fun `caret at the start of a nested statement widens to the statement above`() = assertCodeBlock("""
    class A:
        def f():
    #   └ CARET
            pass
  """, """
    class A:
        def f():
            pass
  """)

  @Test
  fun `caret at the start of a top level statement stays on that statement`() = assertCodeBlock("""
    def f():
    #\ CARET
        pass
  """, """
    def f():
        pass
  """)

  @Test
  fun `caret on whitespace falls back to the preceding element`() = assertCodeBlock("""
    x = 1

    #\ CARET
    y = 2
  """, "x = 1")

  @Test
  fun `nested block statement widens to its enclosing block statement`() = assertCodeBlock("""
    while c:
        if a:
    #   └ CARET
            pass
  """, """
    while c:
        if a:
            pass
  """)

  @Test
  fun `caret outside of any statement has no code block`() = runInEdtAndWait {
    myFixture.configureByText("a.py", "")
    assertNull(PyCodeBlockProvider().getCodeBlockRange(myFixture.editor, myFixture.file))
  }

  @Test
  fun `caret in a comment above a statement has no code block`() = runInEdtAndWait {
    configureWithCaret("""
      # just a comment
      #      └ CARET
    """)
    assertNull(PyCodeBlockProvider().getCodeBlockRange(myFixture.editor, myFixture.file))
  }

  private fun assertCodeBlock(@Language("Python") code: String, expected: String) = runInEdtAndWait {
    configureWithCaret(code)
    val range = PyCodeBlockProvider().getCodeBlockRange(myFixture.editor, myFixture.file)
                ?: error("Expected a code block range at the caret")
    assertEquals(expected.trimIndent(), myFixture.file.text.substring(range.startOffset, range.endOffset))
  }
}

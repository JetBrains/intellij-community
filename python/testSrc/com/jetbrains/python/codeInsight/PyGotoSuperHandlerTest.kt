// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.idea.TestFor
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-91546"], classes = [PyGotoSuperHandler::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyGotoSuperHandlerTest : PyCodeInsightTestCase() {

  @Test
  fun `overriding method navigates to the overridden one`() = assertNavigatesTo("""
    class A:
        def f(self):
            return "base"

    class B(A):
        def f(self):
    #       └ CARET
            return "override"
  """, "f(self):")

  @Test
  fun `navigation starts from the method body as well`() = assertNavigatesTo("""
    class A:
        def f(self):
            return "base"

    class B(A):
        def f(self):
            return "override"
    #              └ CARET
  """, "f(self):")

  @Test
  fun `method declared several classes up is still found`() = assertNavigatesTo("""
    class A:
        def f(self):
            return "base"

    class B(A):
        pass

    class C(B):
        def f(self):
    #       └ CARET
            return "override"
  """, "f(self):")

  @Test
  fun `overriding class attribute navigates to the overridden one`() = assertNavigatesTo("""
    class A:
        attr = 1

    class B(A):
        attr = 2
    #     └ CARET
  """, "attr = 1")

  @Test
  fun `method with no super method does not navigate`() = assertStaysPut("""
    class A:
        def f(self):
    #       └ CARET
            pass
  """)

  @Test
  fun `class attribute with no super attribute does not navigate`() = assertStaysPut("""
    class A:
        attr = 1
    #     └ CARET
  """)

  @Test
  fun `caret outside of a class does nothing`() = assertStaysPut("""
    def f():
        pass
    #   └ CARET
  """)

  @Test
  fun `caret in an empty file does nothing`() = runInEdtAndWait {
    myFixture.configureByText("a.py", "")
    PyGotoSuperHandler().invoke(myFixture.project, myFixture.editor, myFixture.file)
    assertEquals(0, myFixture.caretOffset, "Caret was expected to stay where it was")
  }

  private fun assertNavigatesTo(@Language("Python") code: String, expectedTextAtCaret: String) = runInEdtAndWait {
    configureAndInvoke(code)
    val expectedOffset = myFixture.file.text.indexOf(expectedTextAtCaret)
    assertEquals(expectedOffset, myFixture.editor.caretModel.offset, "Caret did not land on the super element")
  }

  private fun assertStaysPut(@Language("Python") code: String) = runInEdtAndWait {
    val caretBefore = configureAndInvoke(code)
    assertEquals(caretBefore, myFixture.editor.caretModel.offset, "Caret was expected to stay where it was")
  }

  private fun configureAndInvoke(code: String): Int {
    configureWithCaret(code)
    val caretBefore = myFixture.caretOffset
    PyGotoSuperHandler().invoke(myFixture.project, myFixture.editor, myFixture.file)
    return caretBefore
  }
}

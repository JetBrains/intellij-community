// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.highlighting

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.idea.TestFor
import com.intellij.psi.PsiElement
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-91546"], classes = [PyHighlightExitPointsHandler::class, PyHighlightExitPointsHandlerFactory::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyHighlightExitPointsHandlerTest : PyCodeInsightTestCase() {

  @Test
  fun `return keyword highlights all returns of the function`() = assertExitPoints("""
    def f(x):
        if x:
            return 1
    #         └ CARET
        return 2
  """, "return 1", "return 2")

  @Test
  fun `bare return is an exit point too`() = assertExitPoints("""
    def f(x):
        if x:
            return
    #         └ CARET
        return 2
  """, "return", "return 2")

  @Test
  fun `returns of a nested function are not highlighted`() = assertExitPoints("""
    def outer(x):
        def inner():
            return 0
        return 1
    #     └ CARET
  """, "return 1")

  @Test
  fun `implicit fall through does not add a statement`() = assertExitPoints("""
    def f(x):
        if x:
            return 1
    #         └ CARET
  """, "return 1")

  @Test
  fun `raise is not reported as an exit point of the return`() = assertExitPoints("""
    def f(x):
        if x:
            raise ValueError
        return 1
    #     └ CARET
  """, "return 1")

  // The handler collects every statement whose exit target is the function, which includes returns that
  // control flow can never reach.
  @Test
  fun `unreachable return is highlighted as well`() = assertExitPoints("""
    def f(x):
        return 1
    #     └ CARET
        return 2
  """, "return 1", "return 2")

  @Test
  fun `no handler inside the returned expression`() = assertNoHandler("""
    def f(x):
        return x + 1
    #              └ CARET
  """)

  @Test
  fun `no handler outside a return statement`() = assertNoHandler("""
    def f(x):
        y = 1
    #       └ CARET
        return y
  """)

  @Test
  fun `return outside of a function yields no usages`() = assertExitPoints("""
    return 1
    # └ CARET
  """)

  private fun assertExitPoints(code: String, vararg expected: String) = runInEdtAndWait {
    val handler = createHandler(code) ?: error("Expected an exit-points handler at the caret")
    handler.computeUsages(handler.targets)
    val text = myFixture.file.text
    val highlighted = handler.readUsages.sortedBy { it.startOffset }.map { text.substring(it.startOffset, it.endOffset) }
    assertEquals(expected.toList(), highlighted)
    assertTrue(handler.writeUsages.isEmpty())
  }

  private fun assertNoHandler(code: String) = runInEdtAndWait {
    assertNull(createHandler(code))
  }

  private fun createHandler(code: String): HighlightUsagesHandlerBase<PsiElement>? {
    configureWithCaret(code)
    val target = myFixture.file.findElementAt(myFixture.caretOffset)
    assertNotNull(target, "No PSI element at the caret")
    @Suppress("UNCHECKED_CAST")
    return PyHighlightExitPointsHandlerFactory()
      .createHighlightUsagesHandler(myFixture.editor, myFixture.file, target!!) as HighlightUsagesHandlerBase<PsiElement>?
  }
}

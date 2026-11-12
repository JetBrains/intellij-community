// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Rendering of TypedDict types, which cannot be covered by the inline `TYPE` assertions of
 * [com.jetbrains.python.fixtures.PyCodeInsightTestCase] because those never render fully qualified names.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyTypedDictRenderingTest : PyCodeInsightTestCase() {

  @Test
  @TestFor(issues = ["PY-85440"])
  fun `fully qualified name of parameterized TypedDict`() {
    doTestFullyQualifiedTypeHint("mod.Box[builtins.int]", """
      from typing import TypedDict

      class Box[T](TypedDict):
          value: T

      def f(b: Box[int]):
          expr = b
      """.trimIndent())
  }

  @Test
  @TestFor(issues = ["PY-85440"])
  fun `fully qualified name of plain TypedDict`() {
    doTestFullyQualifiedTypeHint("mod.Movie", """
      from typing import TypedDict

      class Movie(TypedDict):
          name: str

      def f(m: Movie):
          expr = m
    """.trimIndent())
  }

  @Test
  @TestFor(issues = ["PY-85440", "PY-91630"])
  fun `cloning does not parameterize generic TypedDict`() {
    doTestFullyQualifiedTypeHint("mod.Box", """
      from typing import TypedDict

      class Box[T](TypedDict):
          value: T

      def f(b: Box):
          expr = b
      """.trimIndent()) { type, context -> PyLiteralType.upcastLiteralToClassDeep(type, context) }
  }

  private fun doTestFullyQualifiedTypeHint(
    expected: String,
    text: String,
    transform: (PyType?, TypeEvalContext) -> PyType? = { type, _ -> type },
  ) {
    myFixture.configureByText("mod.py", text)
    runReadActionBlocking {
      val expr = myFixture.findElementByText("expr", PyExpression::class.java)
      val context = TypeEvalContext.codeAnalysis(expr.project, expr.containingFile)
      assertEquals(expected, PythonDocumentationProvider.getFullyQualifiedTypeHint(transform(context.getType(expr), context), context))
    }
  }
}

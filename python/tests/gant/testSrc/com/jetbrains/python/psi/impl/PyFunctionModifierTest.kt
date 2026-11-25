// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.idea.TestFor
import com.intellij.testFramework.runInEdtAndGet
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.ast.PyAstFunction.Modifier
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * [PyFunctionImpl.getModifier] on the AST finds a wrapping assignment of the method among the later class-level statements.
 */
@Subsystems.CodeInsight
@Layers.Functional
@TestFor(classes = [PyFunctionImpl::class], issues = ["PY-92690"])
class PyFunctionModifierTest : PyCodeInsightTestCase() {

  @Test
  fun `a wrapping assignment after other assignments makes a static method`() {
    assertEquals(Modifier.STATICMETHOD, modifierOfMethodF("""
      class C:
          def f(x):
              pass
          a = 1
          b, c = 2, 3
          f = staticmethod(f)
    """.trimIndent()))
  }

  @Test
  fun `a wrapping assignment in a tuple target makes a class method`() {
    assertEquals(Modifier.CLASSMETHOD, modifierOfMethodF("""
      class C:
          def f(cls):
              pass
          a, f = 1, classmethod(f)
    """.trimIndent()))
  }

  @Test
  fun `a wrapping assignment in a nested tuple target makes a class method`() {
    assertEquals(Modifier.CLASSMETHOD, modifierOfMethodF("""
      class C:
          def f(cls):
              pass
          a, (b, f) = 1, (2, classmethod(f))
    """.trimIndent()))
  }

  @Test
  fun `a chained wrapping assignment makes a static method`() {
    assertEquals(Modifier.STATICMETHOD, modifierOfMethodF("""
      class C:
          def f(x):
              pass
          a = f = staticmethod(f)
    """.trimIndent()))
  }

  @Test
  fun `a wrapping assignment after another assignment to the method name makes a static method`() {
    assertEquals(Modifier.STATICMETHOD, modifierOfMethodF("""
      class Holder:
          pass

      class C:
          def f(x):
              pass
          Holder.f = 1
          f = staticmethod(f)
    """.trimIndent()))
  }

  @Test
  fun `a wrapping assignment to another name does not change the method`() {
    assertNull(modifierOfMethodF("""
      class C:
          def f(self):
              pass
          g = staticmethod(f)
    """.trimIndent()))
  }

  @Test
  fun `an assignment of the method name that wraps another function does not change the method`() {
    assertNull(modifierOfMethodF("""
      class C:
          def f(self):
              pass
          def g():
              pass
          f = staticmethod(g)
    """.trimIndent()))
  }

  private fun modifierOfMethodF(text: String): Modifier? = runInEdtAndGet {
    val file = myFixture.configureByText("a.py", text) as PyFile
    val method = findFunction(file, "C.f") as PyFunctionImpl
    assertNull(method.stub)
    method.modifier
  }
}

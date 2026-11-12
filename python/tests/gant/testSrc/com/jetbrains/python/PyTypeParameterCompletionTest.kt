// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.testFramework.runInEdtAndGet
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.impl.references.PyReferenceImpl
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-92825"], classes = [PyReferenceImpl::class])
@Subsystems.CodeCompletion
@Layers.Functional
class PyTypeParameterCompletionTest : PyCodeInsightTestCase() {

  @Test
  fun `function type parameters in the return annotation`() = assertOffered("""
    def f[Key, Value](a: Key) -> Value: ...
    #                            └ CARET
  """, "Key", "Value")

  @Test
  fun `function type parameters in a parameter annotation`() = assertOffered("""
    def f[Key, Value](a: Key) -> Value: ...
    #                    └ CARET
  """, "Key", "Value")

  @Test
  fun `function type parameters in the function body`() = assertOffered("""
    def f[Key, Value](a: Key) -> Value:
        x: Key
    #      └ CARET
  """, "Key", "Value")

  @Test
  fun `function type parameters not in a default value`() = assertNotOffered("""
    y = 1
    def f[Key](a=x): ...
    #            └ CARET
  """, "Key", visible = "y")

  @Test
  fun `function type parameters not in a decorator`() = assertNotOffered("""
    y = 1
    @X
    #└ CARET
    def f[Key](a: Key) -> Key: ...
  """, "Key", visible = "y")

  @Test
  fun `function type parameters not after the function`() = assertNotOffered("""
    def f[Key](a: Key) -> Key: ...
    x: X
    #  └ CARET
  """, "Key", visible = "f")

  @Test
  fun `nested function type parameters not in the outer function body`() = assertNotOffered("""
    def outer():
        def inner[Key](a: Key) -> Key: ...
        x: X
    #      └ CARET
  """, "Key", visible = "inner")

  @Test
  fun `class type parameters in the base class list`() = assertOffered("""
    class C[T](list[X]): ...
    #               └ CARET
  """, "T")

  @Test
  fun `class type parameters not after the class`() = assertNotOffered("""
    class C[T]: ...
    x: X
    #  └ CARET
  """, "T", visible = "C")

  @Test
  fun `only the earlier class type parameter in a type parameter default`() = assertNotOffered("""
    class C[T, U = X]: ...
    #              └ CARET
  """, "U", visible = "T")

  @Test
  fun `class type parameters in the class body`() = assertOffered("""
    class C[T]:
        attr: X
    #         └ CARET
  """, "T")

  @Test
  fun `class type parameters in the return annotation of a method`() = assertOffered("""
    class C[T]:
        def m(self) -> X: ...
    #                  └ CARET
  """, "T")

  @Test
  fun `class and method type parameters in the return annotation of a generic method`() = assertOffered("""
    class C[T]:
        def m[S](self, a: S) -> X: ...
    #                           └ CARET
  """, "T", "S")

  @Test
  fun `method type parameters not in another method`() = assertNotOffered("""
    class C:
        def m[S](self, a: S) -> S: ...
        def n(self):
            x: X
    #          └ CARET
  """, "S", visible = "self")

  @Test
  fun `type alias type parameters in the aliased type`() = assertOffered("""
    type A[T] = list[X]
    #                └ CARET
  """, "T")

  private fun assertOffered(@Language("Python") text: String, vararg names: String) {
    val variants = variants(text)
    for (name in names) {
      assertTrue(name in variants, "$name is not in $variants")
    }
  }

  private fun assertNotOffered(@Language("Python") text: String, name: String, visible: String) {
    val variants = variants(text)
    assertTrue(visible in variants, "$visible is not in $variants")
    assertFalse(name in variants, "$name is in $variants")
  }

  private fun variants(text: String): List<String> = runInEdtAndGet {
    configureWithCaret(text)
    myFixture.completeBasic()
    myFixture.lookupElementStrings.orEmpty()
  }
}

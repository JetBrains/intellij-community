// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.types.PyTypeFormType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for the PEP 747 `typing.TypeForm` special form.
 */
@TestFor(issues = ["PY-89043"], classes = [PyTypeFormType::class])
class PyTypeFormTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class AnnotationResolution {
    @Test
    fun `TypeForm parameter type`() = test("""
      from typing_extensions import TypeForm

      def f(x: TypeForm[int]):
          y = x
      #   └ TYPE TypeForm[int]
      """)

    @Test
    fun `TypeForm of a complex type expression`() = test("""
      from typing_extensions import TypeForm

      def f(x: TypeForm[int | str]):
          y = x
      #   └ TYPE TypeForm[int | str]
      """)
  }

  @Nested
  inner class Assignability {
    @Test
    fun `class object is assignable to TypeForm`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[int] = int
      """)

    @Test
    fun `TypeForm is covariant`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[int | str] = int
      """)

    @Test
    fun `union of class objects is assignable to TypeForm`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[int | str] = int | str
      """)

    @Test
    fun `wrong class object is not assignable to TypeForm`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[int] = str # ISSUES *
      """)

    @Test
    fun `plain value is not assignable to TypeForm`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[int] = 42 # ISSUES *
      """)
  }

  @Nested
  inner class TypeVariableInference {
    @Test
    fun `infers represented type from a class object`() = test("""
      from typing_extensions import TypeForm

      def f[T](form: TypeForm[T]) -> T: ...

      def use():
          r = f(int)
      #   └ TYPE int
      """)

    @Test
    fun `infers represented type from a union expression`() = test("""
      from typing_extensions import TypeForm

      def f[T](form: TypeForm[T]) -> T: ...

      def use():
          r = f(int | str)
      #   └ TYPE int | str
      """)

    @Test
    fun `infers represented type from a generic alias`() = test("""
      from typing_extensions import TypeForm

      def f[T](form: TypeForm[T]) -> T: ...

      def use():
          r = f(list[int])
      #   └ TYPE list[int]
      """)

    @Test
    fun `reports a plain value argument`() = test("""
      from typing_extensions import TypeForm

      def f[T](form: TypeForm[T]) -> T: ...

      def use():
          f(42) # ISSUES *
      """)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
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


    // PEP 747: bare `TypeForm` is equivalent to `TypeForm[Any]`.
    @Test
    fun `bare TypeForm resolves to TypeForm of Any`() = test("""
      from typing_extensions import TypeForm

      def f(x: TypeForm):
          y = x
      #   └ TYPE TypeForm[Any]
      """)

    @Test
    fun `explicit TypeForm of Any resolves to TypeForm of Any`() = test("""
      from typing import Any
      from typing_extensions import TypeForm

      def f(x: TypeForm[Any]):
          y = x
      #   └ TYPE TypeForm[Any]
      """)

    // Same, but with the `python.type.any` engine off (the production default), where the represented type is
    // `null` rather than the explicit `PyAnyType.Any`. Both must still render as `TypeForm[Any]`.
    @Test
    fun `bare TypeForm resolves to TypeForm of Any with the legacy engine`() = test(
      defaultTestOptions.copy(enablePyAnyType = false), """
      from typing_extensions import TypeForm

      def f(x: TypeForm):
          y = x
      #   └ TYPE TypeForm[Any]
      """)

    @Test
    fun `explicit TypeForm of Any resolves to TypeForm of Any with the legacy engine`() = test(
      defaultTestOptions.copy(enablePyAnyType = false), """
      from typing import Any
      from typing_extensions import TypeForm

      def f(x: TypeForm[Any]):
          y = x
      #   └ TYPE TypeForm[Any]
      """)
  }

  @Nested
  inner class Validation {
    // PEP 747: `TypeForm` may be used unparameterized, so the bare form is not a diagnostic.
    @Test
    fun `bare TypeForm is valid`() = test("""
      from typing_extensions import TypeForm

      def func(x: TypeForm): ...
      """)

    @Test
    fun `TypeForm with too many arguments`() = test("""
      from typing_extensions import TypeForm

      def func(x: TypeForm[int, str]): ... # ISSUES *
      """)

    @Test
    fun `TypeForm with a single argument is valid`() = test("""
      from typing_extensions import TypeForm

      def func(x: TypeForm[int]): ...
      """)
  }

  @Nested
  inner class CallableForm {
    @Test
    fun `explicit TypeForm constructor of a class object`() = test("""
      from typing_extensions import TypeForm

      x = TypeForm(int)
      y = x
      #   └ TYPE TypeForm[int]
      """)

    @Test
    fun `explicit TypeForm constructor of a union expression`() = test("""
      from typing_extensions import TypeForm

      x = TypeForm(str | None)
      y = x
      #   └ TYPE TypeForm[str | None]
      """)

    @Test
    fun `explicit TypeForm constructor of a string forward reference`() = test("""
      from typing_extensions import TypeForm

      x = TypeForm('list[int]')
      y = x
      #   └ TYPE TypeForm[list[int]]
      """)

    @Test
    fun `explicit TypeForm constructor is a valid TypeForm value`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[int | str] = TypeForm(int)
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

    @Test
    fun `string forward reference is assignable to TypeForm`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[str | None] = "str | None"
      """)

    @Test
    fun `simple string forward reference is assignable to TypeForm`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[int] = "int"
      """)

    @Test
    fun `string forward reference is covariant`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[int | str] = "int"
      """)

    @Test
    fun `string forward reference is assignable to TypeForm of Any`() = test("""
      from typing import Any
      from typing_extensions import TypeForm

      x: TypeForm[Any] = "int"
      """)

    @Test
    fun `class object is assignable to TypeForm of Any`() = test("""
      from typing import Any
      from typing_extensions import TypeForm

      x: TypeForm[Any] = int
      """)

    // PEP 747: `TypeForm[Any]` is assignable both to and from any other `TypeForm` type.
    @Test
    fun `TypeForm of Any is assignable to a specific TypeForm`() = test("""
      from typing import Any
      from typing_extensions import TypeForm

      def use(a: TypeForm[Any]):
          x: TypeForm[int] = a
      """)

    @Test
    fun `specific TypeForm is assignable to TypeForm of Any`() = test("""
      from typing import Any
      from typing_extensions import TypeForm

      def use(a: TypeForm[int]):
          x: TypeForm[Any] = a
      """)

    @Test
    fun `multiline string forward reference is assignable to TypeForm`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[int | str] = ${"\"\"\""}
          int | str
      ${"\"\"\""}
      """)

    @Test
    fun `string forward reference to a wrong type is not assignable to TypeForm`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[int] = "str" # ISSUES *
      """)

    @Test
    fun `invalid string forward reference is not assignable to TypeForm`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[int] = "not a type" # ISSUES *
      """)

    @Test
    fun `f-string is not assignable to TypeForm`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[int] = f"int" # ISSUES *
      """)

    @Test
    fun `None is assignable to a TypeForm containing None`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[str | None] = None
      """)

    @Test
    fun `None is assignable to TypeForm of None`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[None] = None
      """)

    @Test
    fun `None is not assignable to a TypeForm without None`() = test("""
      from typing_extensions import TypeForm

      x: TypeForm[int] = None # ISSUES *
      """)
  }

  @Nested
  inner class StringArguments {
    @Test
    fun `string forward reference is a valid TypeForm argument`() = test("""
      from typing_extensions import TypeForm

      def g(x: TypeForm[int | str]): ...

      def use():
          g("int")
      """)

    @Test
    fun `reports a wrong string forward reference argument`() = test("""
      from typing_extensions import TypeForm

      def g(x: TypeForm[int | str]): ...

      def use():
          g("bytes") # ISSUES *
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

    @Test
    fun `infers represented type from a string forward reference`() = test("""
      from typing_extensions import TypeForm

      def f[T](form: TypeForm[T]) -> T: ...

      def use():
          r = f("int")
      #   └ TYPE int
      """)

    @Test
    fun `infers represented type from a string forward reference union`() = test("""
      from typing_extensions import TypeForm

      def f[T](form: TypeForm[T]) -> T: ...

      def use():
          r = f("int | str")
      #   └ TYPE int | str
      """)

    @Test
    fun `infers represented type from a keyword string forward reference`() = test("""
      from typing_extensions import TypeForm

      def f[T](form: TypeForm[T]) -> T: ...

      def use():
          r = f(form="int")
      #   └ TYPE int
      """)

    @Test
    fun `infers represented type from None`() = test("""
      from typing_extensions import TypeForm

      def f[T](form: TypeForm[T]) -> T: ...

      def use():
          r = f(None)
      #   └ TYPE None
      """)
  }

  @Nested
  inner class VersionGating {
    @Test
    fun `typing TypeForm is available since 3_15`() = test(
      defaultTestOptions.copy(languageLevel = LanguageLevel.PYTHON315), """
      from typing import TypeForm

      def f[T](form: TypeForm[T]) -> T: ...

      def use():
          r = f(int)
      #   └ TYPE int
      """)

    @Test
    fun `typing_extensions TypeForm is available on older versions`() = test(
      defaultTestOptions.copy(languageLevel = LanguageLevel.PYTHON313), """
      from typing_extensions import TypeForm

      def f[T](form: TypeForm[T]) -> T: ...

      def use():
          r = f(int)
      #   └ TYPE int
      """)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test


@TestFor(classes = [PyShadowedTypeParameterDefaultInspection::class], issues = ["PY-90274"])
@Subsystems.Inspections
@Layers.Functional
@PyCodeInsightTestCase.TestInspections(enableInspections = [PyShadowedTypeParameterDefaultInspection::class])
class PyShadowedTypeParameterDefaultInspectionTest : PyCodeInsightTestCase() {

  @Test
  fun `None default shadows type parameter default`() = test("""
    def f[T = int](a: T = None):
    #         ^^^ WEAK-WARNING Default of type parameter 'T' is never used because parameter default values take precedence over it
        return a
    """)

  @Test
  fun `literal defaults shadow type parameter default`() = test("""
    def f[T = int](t1: T = 0, t2: T = 1):
    #         ^^^ WEAK-WARNING Default of type parameter 'T' is never used because parameter default values take precedence over it
        pass
    """)

  @Test
  fun `non-literal default shadows type parameter default`() = test("""
    def factory() -> int: ...
    def f[T = int](a: T = factory()):
    #         ^^^ WEAK-WARNING Default of type parameter 'T' is never used because parameter default values take precedence over it
        return a
    """)

  // `f(1)` leaves `T` unsolved, so `T` gets its default `int`.
  @Test
  fun `no warning when None default has union type`() = test("""
    def f[T = int](a: T | int = None):
        return a
    """)

  @Test
  fun `no warning when None default matches None member`() = test("""
    def f[T = int](a: T | None = None) -> T | None:
        return a
    """)

  // `f("x")` leaves `T` unsolved, so `T` gets its default `int`.
  @Test
  fun `no warning when default does not match union member`() = test("""
    def f[T = int](a: T | str = 0) -> T | str:
        return a
    """)

  @Test
  fun `no warning when type parameter only used in return type`() = test("""
    def f[T = int]() -> T: ...
    """)

  @Test
  fun `no warning when parameter has no default value`() = test("""
    def f[T = int](a: T):
        return a
    """)

  @Test
  fun `no warning when type parameter has no default`() = test("""
    def f[T](a: T = 0):
        return a
    """)

  @Test
  fun `ellipsis default shadows type parameter default`() = test("""
    def f[T = int](a: T = ...): ...
    #         ^^^ WEAK-WARNING Default of type parameter 'T' is never used because parameter default values take precedence over it
    """)

  @Test
  @TestCaseOptions(testFileName = "aaa.pyi")
  fun `no warning when default is ellipsis in stub`() = test("""
    def f[T = int](a: T = ...) -> T: ...
    """)

  @Test
  fun `no warning when default is ellipsis in protocol method`() = test("""
    from typing import Protocol

    class A(Protocol):
        def f[T = int](self, a: T = ...) -> T: ...
    """)

  @Test
  fun `no warning when default is ellipsis in overload`() = test("""
    from typing import overload

    @overload
    def f[T = int](a: T = ...) -> T: ...
    @overload
    def f(a: str, b: int) -> str: ...
    def f(a=None, b=0): ...
    """)
}

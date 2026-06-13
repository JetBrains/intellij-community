// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test


@PyCodeInsightTestCase.TestInspections(enableInspections = [PyShadowedTypeParameterDefaultInspection::class])
class PyShadowedTypeParameterDefaultInspectionTest : PyCodeInsightTestCase() {

  @Test
  fun `None default shadows type parameter default`() = test("""
    def f[T = int](a: T = None):
    #       ^^^^^ WARNING Default of type parameter 'T' is never used because a parameter of this type has its own default value
        return a
    """)

  @Test
  fun `literal default shadows type parameter default`() = test("""
    def f[T = int](t1: T = 0, t2: T = 1):
    #       ^^^^^ WARNING Default of type parameter 'T' is never used because a parameter of this type has its own default value
        pass
    """)

  // The `None` default is absorbed by the explicit `| None`, so it does not pin `T`; the PEP 696 default is still used.
  @Test
  fun `no warning when None default has explicit optional annotation`() = test("""
    def f[T = int](a: T | None = None) -> T | None:
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
    def f[T](a: T = None):
        return a
    """)

  @Test
  fun `no warning when default parameter is not a literal`() = test("""
    def factory() -> int: ...
    def f[T = int](a: T = factory()):
        return a
    """)
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyOperatorTypeTest : PyCodeInsightTestCase() {
  @Test
  @TestFor(issues = ["PY-50189"])
  fun `binary operator result leaves out NotImplementedType`() = test("""
    from types import NotImplementedType

    class A:
        def __add__(self, other: object) -> int | NotImplementedType: ...
        def __lt__(self, other: object) -> bool | NotImplementedType: ...

    total = A() + 1
    # └ TYPE int
    less = A() < 1
    # └ TYPE bool
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-50189"])
  fun `binary operator result leaves out the inferred NotImplemented return`() = test("""
    class A:
        def __add__(self, other):
            if isinstance(other, A):
                return A()
            return NotImplemented

    total = A() + A()
    # └ TYPE A
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-50189"])
  fun `augmented assignment result leaves out NotImplementedType`() = test("""
    from types import NotImplementedType

    class A:
        def __iadd__(self, other: object) -> "A | NotImplementedType": ...

    a = A()
    a += 1
    total = a
    # └ TYPE A
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-50189"])
  fun `explicit operator method call keeps NotImplementedType`() = test("""
    from types import NotImplementedType

    class A:
        def __add__(self, other: object) -> int | NotImplementedType: ...

    total = A().__add__(1)
    # └ TYPE int | NotImplementedType
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-50189"])
  fun `operator method that returns only NotImplemented keeps its type`() = test("""
    from types import NotImplementedType

    class A:
        def __add__(self, other: object) -> NotImplementedType: ...

    total = A() + 1
    # └ TYPE NotImplementedType
    """.trimIndent())
}

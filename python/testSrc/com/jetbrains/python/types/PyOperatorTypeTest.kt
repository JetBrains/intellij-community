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

  @Test
  @TestFor(issues = ["PY-94326"])
  fun `chained comparison joins the types of its pairs`() = test("""
    class A:
        def __lt__(self, other: "B") -> str: ...

    class B:
        def __lt__(self, other: "C") -> bytes: ...

    class C:
        def __lt__(self, other: "D") -> int: ...

    class D: ...

    def f(a: A, b: B, c: C, d: D, o: object):
        result = a < b < c
    #   └ TYPE str | bytes
        result = a < b < c < d
    #   └ TYPE str | bytes | int
        result = a < b is o
    #   └ TYPE str | bool
        result = a < b is not o
    #   └ TYPE str | bool
        result = a < b in [c]
    #   └ TYPE str | bool
        result = a < b not in [c]
    #   └ TYPE str | bool
        result = o is b < c
    #   └ TYPE Literal[False] | bytes
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-94326"])
  fun `chained comparison narrows a pair by its truthiness`() = test("""
    from typing import Literal

    class AlwaysTrue:
        def __lt__(self, other: object) -> Literal[True]: ...

    class AlwaysFalse:
        def __lt__(self, other: object) -> Literal[False]: ...

    class Truthy:
        def __bool__(self) -> Literal[True]: ...

    class Falsy:
        def __bool__(self) -> Literal[False]: ...

    class GivesTruthy:
        def __lt__(self, other: object) -> Truthy: ...

    class GivesFalsy:
        def __lt__(self, other: object) -> Falsy: ...

    class B:
        def __lt__(self, other: object) -> bytes: ...

    def f(t: AlwaysTrue, f: AlwaysFalse, gt: GivesTruthy, gf: GivesFalsy, b: B, o: object):
        result = t < b < o
    #   └ TYPE bytes
        result = f < b < o
    #   └ TYPE Literal[False]
        result = gt < b < o
    #   └ TYPE bytes
        result = gf < b < o
    #   └ TYPE Falsy
        result = o is t < o
    #   └ TYPE Literal[False, True]
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-92847"])
  fun `identity comparison`() = test("""
    def f(a: object, b: object):
        1 is 1
    #   ^^^^^^ TYPE bool
        1 is not 1
    #   ^^^^^^^^^^ TYPE bool
        same = a is b
    #   └ TYPE bool
        different = a is not b
    #   └ TYPE bool
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-92847"])
  fun `membership comparison`() = test("""
    def f():
        1 in [1]
    #   ^^^^^^^^ TYPE bool
        1 not in [1]
    #   ^^^^^^^^^^^^ TYPE bool
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-92847"])
  fun `chained identity comparison`() = test("""
    def f(a: object, b: object, c: object, x: int, y: int):
        result = a is b is c
    #   └ TYPE Literal[False] | bool
        result = x < y is c
    #   └ TYPE Literal[False] | bool
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-92847"])
  fun `identity comparison keeps narrowing`() = test("""
    def f(x: int | None):
        is_none = x is None
    #   └ TYPE bool
        if x is not None:
            x
    #       └ TYPE int
        else:
            x
    #       └ TYPE None
    """.trimIndent())
}

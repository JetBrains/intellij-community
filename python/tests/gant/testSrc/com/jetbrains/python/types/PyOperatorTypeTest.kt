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
  @TestFor(issues = ["PY-92887"])
  fun `generic union member overloaded subscription`() = test("""
    from typing import overload

    class Box[T]:
        @overload
        def __getitem__(self, index: int) -> T: ...
        @overload
        def __getitem__(self, index: slice) -> list[T]: ...
        def __getitem__(self, index): ...

    def check(box: Box[int] | Box[str]):
        result = box[0]
    #     └ TYPE int | str
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-92887"])
  fun `generic union member overloaded binary operator`() = test("""
    from typing import overload

    class Box[T]:
        @overload
        def __add__(self, other: int) -> T: ...
        @overload
        def __add__(self, other: str) -> list[T]: ...
        def __add__(self, other): ...

    def check(box: Box[int] | Box[str]):
        result = box + 0
    #     └ TYPE int | str
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-92887"])
  fun `generic union member overloaded inplace operator`() = test("""
    from typing import overload

    class Box[T]:
        @overload
        def __iadd__(self, other: int) -> Box[T]: ...
        @overload
        def __iadd__(self, other: str) -> list[T]: ...
        def __iadd__(self, other): ...

    def check(box: Box[int] | Box[str]):
        box += 0
        result = box
    #     └ TYPE Box[int] | Box[str]
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-92887"])
  fun `generic union member overloaded right operator`() = test("""
    from typing import overload

    class Box[T]:
        @overload
        def __radd__(self, other: int) -> T: ...
        @overload
        def __radd__(self, other: str) -> list[T]: ...
        def __radd__(self, other): ...

    def check(box: Box[int] | Box[str]):
        result = 0 + box
    #     └ TYPE int | str
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-92887"])
  fun `right operator assigned from left operator`() = test("""
    class A:
        def __add__(self, other: "B") -> int: ...

    class B:
        def __add__(self, other: A) -> str: ...
        __radd__ = __add__

    def f(a: A, b: B):
        result = a + b
    #     └ TYPE int
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-92887"])
  fun `inplace operator assigned from other method`() = test("""
    class A:
        def __add__(self, other: int) -> int: ...
        def _iadd(self, other: int) -> str: ...
        __iadd__ = _iadd

    a = A()
    a += 1
    result = a
    #└ TYPE str
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-92887"])
  fun `right operator for generic union member without matching left operator`() = test("""
    class Box[T]:
        def __add__(self, other: T) -> T: ...

    class C:
        def __radd__(self, other: object) -> None: ...

    def f(box: Box[C] | Box[int], c: C):
        result = box + c
    #     └ TYPE C | None
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-92887"])
  fun `chained comparison left operator`() = test("""
    class A:
        def __gt__(self, other: int) -> int: ...
        def __lt__(self, other: "B") -> int: ...

    class B:
        def __gt__(self, other: A) -> str: ...

    def f(x: int, a: A, b: B):
        result = x < a < b
    #     └ TYPE int
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
    #   └ TYPE bool
        result = x < y is c
    #   └ TYPE bool
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

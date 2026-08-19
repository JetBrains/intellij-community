// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.types.PyExpectedTypeJudgement
import org.junit.jupiter.api.Test


@TestFor(classes = [PyExpectedTypeJudgement::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyExpectedTypeJudgmentTest : PyCodeInsightTestCase() {

  override val defaultInspections: Set<Class<out LocalInspectionTool>> = emptySet()

  @Test
  fun `Parenthesis expression`() = test("""
    x : int = (1)
    #          └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Walrus expression`() = test("""
    a : int
    a = (b := 34)
    #         └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Walrus in tuple expression`() = test("""
    x: int
    x, y = ((y := 34), 5)
    #             └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Walrus in parentheses`() = test("""
    b: object
    (b := 34)
    #     └ EXPECTED_TYPE object
    """.trimIndent())

  @Test
  fun `Reassign function parameter`() = test("""
    def f(b: int) :
      b = expr
    #     └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Expression assigned to slice`() = test("""
    a: list[int]
    a[:] = expr
    #      └ EXPECTED_TYPE Iterable[int]
    """.trimIndent())

  @Test
  fun `Nested expression assigned to slice`() = test("""
    a: list[int]
    a[:] = (expr,)
    #       └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Start index in slice`() = test("""
    a: list[int]
    a[start:] = [1]
    #  └ EXPECTED_TYPE int | None
    """.trimIndent())

  @Test
  fun `Stop index in slice`() = test("""
    a: list[int]
    a[:stop] = [1]
    #   └ EXPECTED_TYPE int | None
    """.trimIndent())

  @Test
  fun `Step index in slice`() = test("""
    a: list[int]
    a[::step] = [1]
    #    └ EXPECTED_TYPE int | None
    """.trimIndent())

  @Test
  fun `Tuple as argument`() = test("""
    from typing import Iterable

    def f(xs: Iterable[str]):
        ...
    f((expr, "spam"))
    # ^^^^^^^^^^^^^^ EXPECTED_TYPE Iterable[str]
    """.trimIndent())

  @Test
  fun `Expression inside tuple as argument`() = test("""
    from typing import Iterable

    def f(xs: Iterable[str]):
        ...
    f((expr, "spam"))
    #   └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Expression inside lambda as argument 1`() = test("""
    from typing import Callable

    def f(fn: Callable[[int], object]):
        ...
    f(lambda expr: {})
    #        └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Expression inside lambda as argument 2`() = test("""
    from typing import Callable

    def f(fn: Callable[[int], str]):
        ...
    f(lambda x: expr)
    #           └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Expression inside lambda as untyped argument`() = test("""
    def f(fn):
        ...
    f(lambda expr: 2)
    #        └ EXPECTED_TYPE Unknown
    """.trimIndent())

  @Test
  fun `Expression inside lambda body as untyped argument`() = test("""
    def f(fn):
        ...
    f(lambda x = 2: (x := "hello"))
    #                     └ EXPECTED_TYPE Unknown
    """.trimIndent())

  @Test
  fun `Expression inside lambda body as any typed argument`() = test("""
    from typing import Callable

    def f(fn: Callable[[Any], Any]):
        ...
    f(lambda x = 2: (x := "hello"))
    #                     └ EXPECTED_TYPE Unknown
    """.trimIndent())

  @Test
  fun `Expression inside lambda body as int typed argument`() = test("""
    from typing import Callable

    def f(fn: Callable[[int], Any]):
        ...
    f(lambda x: (x := "hello"))
    #                 └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Expression inside lambda body as int typed return`() = test("""
    from typing import Callable

    def f(fn: Callable[[Any], int]):
        ...
    f(lambda x: (x := "hello"))
    #                 └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Expression inside lambda as argument typed as union`() = test("""
    from typing import Callable

    def f(fn: str|Callable[[int], object]):
        ...
    f(lambda expr: {})
    #        └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Expression as return value`() = test("""
    from typing import Iterable

    def f(xs) -> str:
        return expr
    #          └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Expression in call target as return value`() = test("""
    def main() -> int:
        return expr()
    #          ^^^^^^ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Tuple as return value`() = test("""
    from typing import Iterable

    def f(xs) -> Iterable[str]:
        return (expr, "spam")
    #          │ └ EXPECTED_TYPE str
    #          ^^^^^^^^^^^^^^ EXPECTED_TYPE Iterable[str]
    """.trimIndent())

  @Test
  fun `Expression inside lambda as return value 1`() = test("""
    from typing import Callable

    def f() -> Callable[[int], str]:
      return lambda expr: "r"
    #               └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Expression inside lambda as return value 2`() = test("""
    from typing import Callable

    def f() -> Callable[[int], str]:
      return lambda x: expr
    #                  └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Expression in assignment to attribute`() = test("""
    class A:
        a : int = 1

    A.a = expr
    #     └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Expression inside lambda of generic function`() = test("""
    from collections.abc import Callable, Iterable

    def f[T](x: Iterable[T], y: Callable[[T], object]): ...

    f([1], lambda expr: ...)
    #             └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-85922"])
  fun `Expression inside generic class as return value 1`() = test("""
    from typing import Callable

    class A[T]:
        def f(self, fn: Callable[[T], str]) -> float: ...

    A[int]().f(lambda expr: "s")
    #                 └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-85922"])
  fun `Expression inside generic class as return value 2`() = test("""
    from typing import Callable

    class A[T]:
        def f(self, fn: Callable[[str], T]) -> float: ...

    A[int]().f(lambda x: expr)
    #                    └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Tuple as return value no type hint`() = test("""
    def f(xs):
        return (expr, "spam")
    #          │ └ EXPECTED_TYPE Unknown
    #          ^^^^^^^^^^^^^^ EXPECTED_TYPE Unknown
    """.trimIndent())

  @Test
  fun `Tuple as assignment value`() = test("""
    x2: str
    x1, (x2, x3) = (42, (expr, "spam"))
    #              │     └ EXPECTED_TYPE str
    #              ^^^^^^^^^^^^^^^^^^^^ EXPECTED_TYPE tuple[Unknown, tuple[str, Unknown]]
    """.trimIndent())

  @Test
  fun `Tuple as assignment value no type hint`() = test("""
    x1, (x2, x3) = (42, (expr, "spam"))
    #              │     └ EXPECTED_TYPE Unknown
    #              ^^^^^^^^^^^^^^^^^^^^ EXPECTED_TYPE tuple[Unknown, tuple[Unknown, Unknown]]
    """.trimIndent())

  @Test
  fun `Expr as assignment value no type hint`() = test("""
    x1, (x2, x3) = expr
    #              └ EXPECTED_TYPE Iterable[Unknown]
    """.trimIndent())

  @Test
  fun `Expression as assignment value to list`() = test("""
    x: list[int]
    x[0] = expr
    #      └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Expression inside tuple as assignment value to list`() = test("""
    x1: bool
    x2: str
    x3: int
    x1, [x2, x3] = (true, (expr, "spam"))
    #                      └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Expression inside tuple as assignment value to list no type hint`() = test("""
    x1, [x2, x3] = (42, (expr, "spam"))
    #                    └ EXPECTED_TYPE Unknown
    """.trimIndent())

  @Test
  fun `Expression as assignment value to unwrap 1`() = test("""
    x: int
    xs: tuple[int, ...]
    x, *xs = expr
    #        └ EXPECTED_TYPE Iterable[int]
    """.trimIndent())

  @Test
  fun `Expression as tuple element to unwrap 1`() = test("""
    x: int
    xs: tuple[int, ...]
    x, *xs = 1, 2, expr
    #              └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Expression as assignment value to unwrap 2`() = test("""
    x: int
    xs: tuple[int, str]
    x, *xs = expr
    #        └ EXPECTED_TYPE Iterable[int | str]
    """.trimIndent())

  @Test
  fun `Expression as tuple element to unwrap 2`() = test("""
    x: int
    xs: tuple[int, str]
    x, *xs = 1, 2, expr
    #              └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Expression as tuple element to unwrap 2 out of bounds`() = test("""
    x: int
    xs: tuple[int, str]
    x, *xs = 1, 2, "3", expr
    #                   └ EXPECTED_TYPE Unknown
    """.trimIndent())

  @Test
  fun `Expression in variadic tuple 1`() = test("""
    x: tuple[str, *tuple[int, ...]] = "s", 2, 3
    #                                 │    │  └ EXPECTED_TYPE int
    #                                 │    └ EXPECTED_TYPE int
    #                                 └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Expression in variadic tuple 2`() = test("""
    x: tuple[str, *tuple[int, ...], float] = "s", 2, 3.14
    #                                        │    │  └ EXPECTED_TYPE float | int
    #                                        │    └ EXPECTED_TYPE int
    #                                        └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Expression in variadic tuple 3`() = test("""
    x: tuple[*tuple[int, ...], str] = 1, 2, "s"
    #                                 │  │  └ EXPECTED_TYPE str
    #                                 │  └ EXPECTED_TYPE int
    #                                 └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Subscription expression`() = test("""
    from typing import Literal

    d: dict[Literal["1", 2, "foo"], str] = {}
    d[expr]
    #  └ EXPECTED_TYPE Literal["1", 2, "foo"]
    """.trimIndent())

  @Test
  fun `Argument for args`() = test("""
    def f(*args: str):
        pass

    f(expr)
    #  └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Argument for args of unpacked tuple 1`() = test("""
    def f(*args: *tuple[int]):
        pass

    f(expr)
    #  └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Argument for args of unpacked tuple 2`() = test("""
    def f(*args: *tuple[int]):
        pass

    f(1, expr)
    #    └ EXPECTED_TYPE Unknown
    """.trimIndent())

  @Test
  fun `Argument for args of unpacked tuple 3`() = test("""
    def f(*args: *tuple[int,str]):
        pass

    f(1, expr)
    #    └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Argument for args of unpacked tuple 4`() = test("""
    def f(*args: *tuple[int,...]):
        pass

    f(1, expr)
    #    └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Argument value for kw args`() = test("""
    def f(**kwargs: str):
        pass

    f(foo="value")
    #      └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Argument key for kw args`() = test("""
    def f(**kwargs: str):
        pass

    f(foo="value")
    #  └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Argument for plain parameter`() = test("""
    def f(x: int, y: str):
        pass

    f(42, expr)
    #     └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Value for trivial assignment`() = test("""
    x: str = expr
    #        └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Lambda in assignment`() = test("""
    from typing import Callable

    adder: Callable[[str, int], int] = lambda p_x, p_y: p_x + p_y
    #                                  │      │    └ EXPECTED_TYPE int
    #                                  │      └ EXPECTED_TYPE str
    #                                  ^^^^^^^^^^^^^^^^^^^^^^^^^^ EXPECTED_TYPE (str, int) -> int
    """.trimIndent())

  @Test
  fun `Return of lambda in assignment`() = test("""
    from typing import Callable

    adder: Callable[[str, int], int] = lambda p_x, p_y: p_x + p_y
    #                                                   ^^^^^^^^^ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Nested lambda`() = test("""
    from typing import Callable

    func: Callable[[int], Callable[[float], str]] = lambda xx: lambda yy: "Hi"
    #                                                                 └ EXPECTED_TYPE float | int
    """.trimIndent())

  @Test
  fun `Lambda in assignment previously typed`() = test("""
    from typing import Callable

    adder: Callable[[str, int], int]
    adder = lambda p_x, p_y: p_x + p_y
    #       │      │    └ EXPECTED_TYPE int
    #       │      └ EXPECTED_TYPE str
    #       ^^^^^^^^^^^^^^^^^^^^^^^^^^ EXPECTED_TYPE (str, int) -> int
    """.trimIndent())

  @Test
  fun `Return of lambda in assignment previously typed`() = test("""
    from typing import Callable

    adder: Callable[[str, int], int]
    adder = lambda p_x, p_y: p_x + p_y
    #                        ^^^^^^^^^ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Nested lambda previously typed`() = test("""
    from typing import Callable

    func: Callable[[int], Callable[[float], str]]
    func = lambda xx: lambda yy: "Hi"
    #                        └ EXPECTED_TYPE float | int
    """.trimIndent())

  @Test
  fun `Lambda in assignment typed as attribute`() = test("""
    from typing import Callable

    class C:
        attr: Callable[[str, int], int]
        def __init__(self):
            self.attr = lambda p_x, p_y: p_x + p_y
    #                   │      │    │    ^^^^^^^^^ EXPECTED_TYPE int
    #                   │      │    └ EXPECTED_TYPE int
    #                   │      └ EXPECTED_TYPE str
    #                   ^^^^^^^^^^^^^^^^^^^^^^^^^^ EXPECTED_TYPE (str, int) -> int
    """.trimIndent())

  @Test
  fun `Nested lambda typed as attribute`() = test("""
    from typing import Callable

    class C:
        attr: Callable[[int], Callable[[float], str]]
        def __init__(self):
            self.attr = lambda xx: lambda yy: "Hi"
    #                                     └ EXPECTED_TYPE float | int
    """.trimIndent())

  @Test
  fun `List literal`() = test("""
    from typing import List

    v: List[int] = [expr, 2, 3]
    #              │ └ EXPECTED_TYPE int
    #              ^^^^^^^^^^^^ EXPECTED_TYPE list[int]
    """.trimIndent())

  @Test
  fun `Star argument expression in list`() = test("""
    from typing import List

    v: List[int] = [1, *expr, 4]
    #                   └ EXPECTED_TYPE Iterable[int]
    """.trimIndent())

  @Test
  fun `Expression in star argument expression in list`() = test("""
    from typing import List

    v: List[int] = [1, *[expr, 3], 4]
    #                    └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Dict literal as argument`() = test("""
    v: dict[str, int] = {'key': expr}
    #                   │ │     └ EXPECTED_TYPE int
    #                   │ └ EXPECTED_TYPE str
    #                   ^^^^^^^^^^^^^ EXPECTED_TYPE dict[str, int]
    """.trimIndent())

  @Test
  fun `Star argument in dict literal`() = test("""
    v: dict[str, int] = {'key': 1, **expr}
    #                                └ EXPECTED_TYPE Mapping[str, int]
    """.trimIndent())

  @Test
  fun `Double star expression own type should be any`() = test("""
    ys: dict[str, int] = {**xs}
    #                     ^^^^ EXPECTED_TYPE Unknown
    """.trimIndent())

  @Test
  fun `Key in star argument in dict literal`() = test("""
    v: dict[str, int] = {'key': 1, **{expr: 2}}
    #                                 └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Value in star argument in dict literal`() = test("""
    v: dict[str, int] = {'key': 1, **{"otherKey": expr}}
    #                                             └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Set literal as argument`() = test("""
    from typing import Set

    v: Set[int] = {expr, 2, 3}
    #             │ └ EXPECTED_TYPE int
    #             ^^^^^^^^^^^^ EXPECTED_TYPE set[int]
    """.trimIndent())

  @Test
  fun `Non starred expression as argument`() = test("""
    def f(*args: int):
        pass

    f(expr)
    #  └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Starred expression as argument`() = test("""
    def f(*args: int):
        pass

    f(*expr)
    #   └ EXPECTED_TYPE tuple[int, ...]
    """.trimIndent())

  @Test
  fun `Starred expression element as argument 1`() = test("""
    def f(*args: int):
        pass

    f(*(123, 456))
    #   └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Starred expression element as argument 2`() = test("""
    def f(s: str, *args: int):
        pass

    f("foo", *(123, 456))
    #          └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Starred expression element as argument 3`() = test("""
    def f(s: str, n: int):
        pass

    f(*("foo", 123))
    #          └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Double starred expression element as argument 1`() = test("""
    def f(**kwargs: int):
        pass

    f(**{"s": 123, "n": 456})
    #         └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Double starred expression element as argument 2`() = test("""
    def f(s: str, **kwargs: int):
        pass

    f("foo", **{"s2": 123, "n": 456})
    #                 └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Double starred expression element as argument 3`() = test("""
    def f(s: str, n: int):
        pass

    f(**{"s": "foo", "n": 123})
    #                     └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Double starred expression element as argument 1 b`() = test("""
    from typing import TypedDict, Unpack

    class FArgs(TypedDict):
        s: str
        n: int

    def f(**kwargs: Unpack[FArgs]):
        pass

    f(**{"s": "foo", "n": 123})
    #                     └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Double starred expression element as argument 2 b`() = test("""
    from typing import TypedDict, Unpack

    class FArgs(TypedDict):
        s: str
        n: int

    def f(s: str, **kwargs: Unpack[FArgs]):
        pass

    f("foo", **{"s": "foo", "n": 123})
    #                            └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Double starred expression element as argument combining unpacked typed dict and other parameter types`() = test("""
    from typing import TypedDict, Unpack

    class FArgs(TypedDict):
        s: str
        n: int

    def f(a: str, **kwargs: Unpack[FArgs]):
        pass

    f(**{"s": "foo", "n": 123, "a": expr})
    #                               └ EXPECTED_TYPE str
    f(**{"s": "foo", "n": expr, "a": "bar"})
    #                     └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Double starred expression element as argument both kwargs and named parameter`() = test("""
    def f(a: str, **kwargs: int):
        pass

    f(**{"a": expr, "n": 123})
    #         └ EXPECTED_TYPE str
    f(**{"a": "foo", "n": expr})
    #                     └ EXPECTED_TYPE Unknown FIXME int # PY-85421 Requires constructing an anonymous TypedDict with `extra_items=int`
    """.trimIndent())

  @Test
  fun `Generic method argument`() = test("""
    class Box[T]:
        def m(self, x: T):
            ...
    b: Box[str]
    b.m(expr)
    #    └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Generic function argument`() = test("""
    def f[T](x: T, y: T): ...

    f(42, expr)
    #     └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Star expression own type should be int`() = test("""
    ys: list[int] = [1, *xs]
    #                   ^^^ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Star expression in set literal`() = test("""
    ys: set[int] = {1, *xs}
    #                   └ EXPECTED_TYPE Iterable[int]
    """.trimIndent())

  @Test
  fun `Star expression in tuple literal`() = test("""
    ys: tuple[int, ...] = (1, *xs)
    #                          └ EXPECTED_TYPE Iterable[int]
    """.trimIndent())

  @Test
  fun `Expression in tuple literal`() = test("""
    ys: tuple[int, ...] = [1, x, 3]
    #                         └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Expr non double starred expression as argument`() = test("""
    def f(**kwargs: int):
        pass

    f(param = expr)
    #         └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Param non double starred expression as argument`() = test("""
    def f(**kwargs: str):
        pass

    f(param = expr)
    #  └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Double starred expression as argument`() = test("""
    def f(**kwargs: str):
        pass

    f(**expr)
    # ^^^^^^ EXPECTED_TYPE dict[str, str]
    """.trimIndent())

  @Test
  fun `Double starred expression key as argument`() = test("""
    def f(**kwargs: str):
        pass

    f(**{"key" : 0})
    #     └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Double starred expression value as argument`() = test("""
    def f(**kwargs: str):
        pass

    f(**{"key" : 0})
    #            └ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  fun `Argument of overloaded functions`() = test("""
    from typing import overload

    @overload
    def f(x: int) -> int: ...

    @overload
    def f(x: str) -> str: ...

    def f(x): return x

    f(expr)
    #  └ EXPECTED_TYPE int | str
    """.trimIndent())

  @Test
  fun `Argument of overloaded functions bounded by return`() = test("""
    from typing import overload

    @overload
    def f(x: int) -> int: ...

    @overload
    def f(x: str) -> str: ...

    def f(x): return x

    a: str = f(expr)
    #          └ EXPECTED_TYPE int | str FIXME str # Depends on correct function overload matching
    """.trimIndent())

  @Test
  fun `Return of overloaded functions`() = test("""
    from typing import overload

    @overload
    def f(x: int) -> int: ...

    @overload
    def f(x: str) -> str: ...

    def f(x): return x

    expr = f(1)
    # └ EXPECTED_TYPE Unknown
    """.trimIndent())

  @Test
  fun `Return in async function`() = test("""
    async def foo() -> object:
        return expr
    #          └ EXPECTED_TYPE object
    """.trimIndent())

  @Test
  fun `Yield expression in typed generator`() = test("""
    from typing import Generator

    def f() -> Generator[int, str, float]:
        receive = yield send
    #                    └ EXPECTED_TYPE int
        return result
    #          └ EXPECTED_TYPE float | int
    """.trimIndent())

  @Test
  fun `Yield expression from generator`() = test("""
    from typing import Generator

    def main() -> Generator[int]:
        yield from expr
    #              └ EXPECTED_TYPE Iterable[int]
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-87340"])
  fun `Mismatch of expected and actual tuple size`() = test("""
    def check() -> tuple[bool, int, int]:
        return true, x
    #                └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Comparison expected type less than`() = test("""
    if expr < other:
    #  │       └ EXPECTED_TYPE _Supports__gt__ | Any
    #  └ EXPECTED_TYPE _Supports__lt__ | Any
        pass
    """.trimIndent())

  @Test
  fun `Comparison expected type greater than`() = test("""
    if expr > other:
    #  │       └ EXPECTED_TYPE _Supports__lt__ | Any
    #  └ EXPECTED_TYPE _Supports__gt__ | Any
        pass
    """.trimIndent())

  @Test
  fun `Comparison expected type less than or equal`() = test("""
    if expr <= other:
    #  │        └ EXPECTED_TYPE _Supports__ge__ | Any
    #  └ EXPECTED_TYPE _Supports__le__ | Any
        pass
    """.trimIndent())

  @Test
  fun `Comparison expected type greater than or equal`() = test("""
    if expr >= other:
    #  │        └ EXPECTED_TYPE _Supports__le__ | Any
    #  └ EXPECTED_TYPE _Supports__ge__ | Any
        pass
    """.trimIndent())

  @Test
  fun `Comparison expected type equality`() = test("""
    if expr == other:
    #  │        └ EXPECTED_TYPE _Supports__eq__ | Any
    #  └ EXPECTED_TYPE _Supports__eq__ | Any
        pass
    """.trimIndent())

  @Test
  fun `Comparison expected type inequality`() = test("""
    if expr != other:
    #  │        └ EXPECTED_TYPE _Supports__ne__ | Any
    #  └ EXPECTED_TYPE _Supports__ne__ | Any
        pass
    """.trimIndent())

  @Test
  fun `Comparison expected type membership`() = test("""
    if item in container:
    #  │       └ EXPECTED_TYPE _Supports__contains__
    #  └ EXPECTED_TYPE Unknown
        pass
    """.trimIndent())

  @Test
  fun `Comparison expected type not in`() = test("""
    if item not in container:
    #  │           └ EXPECTED_TYPE _Supports__contains__
    #  └ EXPECTED_TYPE Unknown
        pass
    """.trimIndent())

  @Test
  fun `Comparison expected type is`() = test("""
    if expr is other:
    #  │        └ EXPECTED_TYPE Unknown
    #  └ EXPECTED_TYPE Unknown
        pass
    """.trimIndent())

  @Test
  fun `Comparison expected type is not`() = test("""
    if expr is not other:
    #  │            └ EXPECTED_TYPE Unknown
    #  └ EXPECTED_TYPE Unknown
        pass
    """.trimIndent())

  @Test
  fun `Binary expression expected type multiply list by int`() = test("""
    xs: list[int]
    ys: list[int] = xs * 2
    #               │    └ EXPECTED_TYPE _Supports__index__
    #               └ EXPECTED_TYPE _Supports__mul__ | Any
    """.trimIndent())

  @Test
  fun `Binary expression expected type multiply int by list`() = test("""
    xs: list[int]
    ys: list[int] = 2 * xs
    #               │   └ EXPECTED_TYPE _Supports__rmul__ | Any
    #               └ EXPECTED_TYPE _Supports__index__
    """.trimIndent())

  @Test
  fun `Binary expression expected type numeric addition respects expected result type`() = test("""
    x: float = 1 + 2
    #          │   └ EXPECTED_TYPE _Supports__radd__ | Any
    #          └ EXPECTED_TYPE _Supports__add__ | Any
    """.trimIndent())

  @Test
  fun `Binary literal combination int plus int`() = test("""
    x: int = 1 + 2
    #        │   └ EXPECTED_TYPE _Supports__radd__ | Any
    #        └ EXPECTED_TYPE _Supports__add__ | Any
    """.trimIndent())

  @Test
  fun `Binary literal combination str plus str`() = test("""
    x: str = expr + "s"
    #        │      └ EXPECTED_TYPE _Supports__radd__ | Any
    #        └ EXPECTED_TYPE _Supports__add__ | Any
    """.trimIndent())

  @Test
  fun `Binary literal combination list plus list`() = test("""
    x: list[int] = expr + [1]
    #              │      └ EXPECTED_TYPE _Supports__radd__ | Any
    #              └ EXPECTED_TYPE _Supports__add__ | Any
    """.trimIndent())

  @Test
  fun `Binary literal combination int mult int`() = test("""
    x: int = expr * 2
    #        │      └ EXPECTED_TYPE _Supports__rmul__ | Any
    #        └ EXPECTED_TYPE _Supports__mul__ | Any
    """.trimIndent())

  @Test
  fun `Binary literal combination list mult int`() = test("""
    x: list[int] = expr * 2
    #              │      └ EXPECTED_TYPE _Supports__rmul__ | Any
    #              └ EXPECTED_TYPE _Supports__mul__ | Any
    """.trimIndent())

  @Test
  fun `Binary literal combination list mult list literal`() = test("""
    x: list[int] = expr * [1]
    #              │      ^^^ EXPECTED_TYPE _Supports__rmul__ | Any
    #              └ EXPECTED_TYPE _Supports__index__
    """.trimIndent())

  @Test
  fun `Binary literal combination set or set set literal operand`() = test("""
    x: set[int] = expr | {1}
    #             │      ^^^ EXPECTED_TYPE _Supports__ror__ | Any
    #             └ EXPECTED_TYPE _Supports__or__ | Any
    """.trimIndent())

  @Test
  fun `Argument is list of lists complete literal`() = test("""
    def foo(lst: list[list[int | str]]) -> None: ...
    foo([[1], ["a"]])
    #   ││    ^^^^^ EXPECTED_TYPE list[int | str]
    #   │^^^ EXPECTED_TYPE list[int | str]
    #   ^^^^^^^^^^^^ EXPECTED_TYPE list[list[int | str]]
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-90097"])
  fun `List comprehension with enums 1`() = test("""
    from enum import Enum
    class Tile(Enum):
        EMPTY = 0
        WALL = 1
    bar: list[Tile] = [Tile.EMPTY for _ in range(5)]
    #                  ^^^^^^^^^^ EXPECTED_TYPE Tile
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-90097"])
  fun `List comprehension with enums 2`() = test("""
    from enum import Enum
    class Tile(Enum):
        EMPTY = 0
        WALL = 1
    foo: list[list[Tile]] = [[Tile.EMPTY for _ in range(5)] for _ in range(5)]
    #                         ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ EXPECTED_TYPE list[Tile]
    """.trimIndent())

  @Test
  fun `Result expr in list comprehension as argument`() = test("""
    def foo(xs: list[int]): ...
    foo([idx for idx in range(5)])
    #    └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Result expr in nested list comprehension as argument`() = test("""
    def foo(xs: list[list[int]]): ...
    foo([[idx for idx in range(5)] for _ in range(5)])
    #    │ └ EXPECTED_TYPE int
    #    ^^^^^^^^^^^^^^^^^^^^^^^^^ EXPECTED_TYPE list[int]
    """.trimIndent())

  @Test
  fun `Result expr in list comprehension assigned to typed target`() = test("""
    xs: list[int] = [idx for idx in range(5)]
    #                └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Result expr in list comprehension as return`() = test("""
    def foo() -> list[int]:
        return [idx for idx in range(5)]
    #           └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Result expr in set comprehension as argument`() = test("""
    def foo(xs: set[int]): ...
    foo({idx for idx in range(5)})
    #    └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Result expr in set comprehension assigned to typed target`() = test("""
    xs: set[int] = {idx for idx in range(5)}
    #               └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  @TestCaseOptions(assertRecursionPrevention = false)
  fun `Key in dict comprehension as argument`() = test("""
    def foo(d: dict[int, str]): ...
    foo({idx: str(idx) for idx in range(5)})
    #    └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  @TestCaseOptions(assertRecursionPrevention = false)
  fun `Value in dict comprehension as argument`() = test("""
    def foo(d: dict[int, str]): ...
    foo({i: str(i) for i in range(5)})
    #       ^^^^^^ EXPECTED_TYPE str
    """.trimIndent())

  @Test
  @TestCaseOptions(assertRecursionPrevention = false)
  fun `Key in dict comprehension assigned to typed target`() = test("""
    d: dict[int, str] = {idx: str(idx) for idx in range(5)}
    #                    └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Result expr in generator expression as iterable argument`() = test("""
    from typing import Iterable
    def foo(xs: Iterable[int]): ...
    foo(idx for idx in range(5))
    #   └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Result expr in generator expression passed to list literal`() = test("""
    xs: list[int] = [idx for idx in range(5)]
    #                └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Result expr in generator expression passed to list constructor`() = test("""
    xs: list[int] = list(idx for idx in range(5))
    #                    └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Result expr in generator expression as return`() = test("""
    from typing import Iterator
    def foo() -> Iterator[int]:
        return (idx for idx in range(5))
    #           └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Result expr in async list comprehension as argument`() = test("""
    from typing import Callable
    async def asyncgen():
        yield 10
    async def run(foo: Callable[[list[int]], None]):
        foo([idx async for idx in asyncgen()])
    #        └ EXPECTED_TYPE int
    """.trimIndent())

  @Test
  fun `Result expr in async generator expression as argument`() = test("""
    from typing import AsyncIterable, Callable
    async def asyncgen():
        yield 10
    async def run(foo: Callable[[AsyncIterable[int]], None]):
        foo(idx async for idx in asyncgen())
    #       └ EXPECTED_TYPE int
    """.trimIndent())

  @TestFor(issues = ["PY-91341"])
  @Test
  fun `Sequence unpacking with shorter list literal on rhs`() = test("""
    a, b = [1]
    #      ^^^ EXPECTED_TYPE Iterable[Unknown]
    """.trimIndent())
}

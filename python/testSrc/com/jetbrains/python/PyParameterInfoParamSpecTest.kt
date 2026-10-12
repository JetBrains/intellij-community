// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.fixtures.PyParameterInfoTestCase
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

@TestFor(classes = [PyParameterInfoHandler::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyParameterInfoParamSpecTest : PyCodeInsightTestCase() {

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `class ParamSpec substituted with a parameter list`() = assertParameterInfo("""
    class A[**P]:
        def f(self, *args: P.args, **kwargs: P.kwargs): ...

    a = A[[int, str]]()

    a.f()
    #   └ CARET
  """, "__p0: int, __p1", "__p0: int, ")

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `class ParamSpec substituted with a parameter list with all hints`() = assertParameterInfo("""
    class A[**P]:
        def f(self, *args: P.args, **kwargs: P.kwargs): ...

    a = A[[int, str]]()

    a.f()
    #   └ CARET
  """, "__p0: int, __p1: str", "__p0: int, ", showAllHints = true)

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `class ParamSpec substituted with a parameter list at the second argument`() = assertParameterInfo("""
    class A[**P]:
        def f(self, *args: P.args, **kwargs: P.kwargs): ...

    a = A[[int, str]]()

    a.f(1, )
    #      └ CARET
  """, "__p0, __p1: str", "__p1: str")

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `Callable with a parameter list`() = assertParameterInfo("""
    from typing import Callable

    def g(c: Callable[[int, str], None]):
        c()
    #     └ CARET
  """, "__p0: int, __p1", "__p0: int, ")

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `ParamSpec decorator that adds a parameter with Concatenate`() = assertParameterInfo("""
    from typing import Callable, Concatenate

    def decorator[**P, R](func: Callable[P, R]) -> Callable[Concatenate[str, P], R]: ...

    @decorator
    def function(input_a: int, input_b: float): ...

    function("", )
    #            └ CARET
  """, "__p0, input_a: int, input_b", "input_a: int, ")

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `equal parameters at the first argument`() = assertParameterInfo("""
    class A[**P]:
        def f(self, *args: P.args, **kwargs: P.kwargs): ...

    a = A[[int, int]]()

    a.f(1, 2)
    #   └ CARET
  """, "__p0: int, __p1", "__p0: int, ")

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `equal parameters at the second argument`() = assertParameterInfo("""
    class A[**P]:
        def f(self, *args: P.args, **kwargs: P.kwargs): ...

    a = A[[int, int]]()

    a.f(1, 2)
    #      └ CARET
  """, "__p0, __p1: int", "__p1: int")

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `equal parameters before the next parameter`() = assertParameterInfo("""
    from typing import Callable

    def g(c: Callable[[int, int, str], None]):
        c(1, 2, )
    #           └ CARET
  """, "__p0, __p1, __p2: str", "__p2: str")

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `unnamed positional container`() = assertParameterInfo("""
    def f(*args: *tuple[int, *tuple[str, ...]]): ...

    f()
    # └ CARET
  """, "__p0: int, __args", "__p0: int, ")

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `unnamed positional container at the second argument`() = assertParameterInfo("""
    def f(*args: *tuple[int, *tuple[str, ...]]): ...

    f(1, )
    #    └ CARET
  """, "__p0, *__args: str", "*__args: str")

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `synthetic name does not clash with an unpacked tuple element`() = assertParameterInfo("""
    from typing import Callable, Concatenate

    def decorator[**P, R](func: Callable[P, R]) -> Callable[Concatenate[bytes, P], R]: ...

    @decorator
    def function(*args: *tuple[int, str]): ...

    function()
    #        └ CARET
  """, "__p0: bytes, __p1: int, __p2: str", "__p0: bytes, ", showAllHints = true)

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `unpacked tuple elements are numbered by their position`() = assertParameterInfo("""
    def f(a: int, *args: *tuple[str, bytes]): ...

    f()
    # └ CARET
  """, "a: int, __p1: str, __p2: bytes", "a: int, ", showAllHints = true)

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `synthetic name does not clash with a real parameter name`() = assertParameterInfo("""
    def f(__p1: int, *args: *tuple[str, bytes]): ...

    f()
    # └ CARET
  """, "__p1: int, __p2: str, __p3: bytes", "__p1: int, ", showAllHints = true)

  @Test
  @TestFor(issues = ["PY-93094"])
  fun `variadic keyword argument for unpacked TypedDict`() = assertParameterInfo("""
    from typing import TypedDict, Unpack

    class Movie(TypedDict):
        name: str
        year: int

    def foo(a: int, **b: Unpack[Movie]): ...

    def g(d: Movie):
        foo(1, **d)
    #           └ CARET
  """, "a: int, *, name: str, year: int", "name: str, ", "year: int", showAllHints = true)

  private fun assertParameterInfo(
    @Language("Python") code: String,
    expectedText: String,
    vararg expectedHighlighted: String,
    showAllHints: Boolean = false,
  ) {
    runInEdtAndWait {
      val file = configureWithCaret(code)
      PyParameterInfoTestCase.feignCtrlP(myFixture.caretOffset, file, showAllHints, myFixture.editor)
        .check(expectedText, arrayOf(*expectedHighlighted))
    }
  }
}

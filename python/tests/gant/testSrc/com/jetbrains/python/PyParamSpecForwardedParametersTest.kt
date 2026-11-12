// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.fixtures.PyParameterInfoTestCase
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-88038"])
@Subsystems.CodeInsight
@Layers.Functional
class PyParamSpecForwardedParametersTest : PyCodeInsightTestCase() {

  @Test
  @TestFor(classes = [PyParameterInfoHandler::class])
  fun `parameter info shows the parameters of the class forwarded with a ParamSpec`() = assertParameterInfo("""
    from typing import Callable

    def add[**P, T](cls: Callable[P, T], *args: P.args, **kwargs: P.kwargs) -> T: ...

    class User:
        def __init__(self, name: str, age: int): ...

    add(User, )
    #         └ CARET
  """, "cls: (**P) -> T, name: str, age: int", "name: str, ")

  @Test
  @TestFor(classes = [PyParameterInfoHandler::class])
  // The issue reports the old-style declarations of the type parameters.
  fun `parameter info shows the parameters forwarded with an old-style ParamSpec`() = assertParameterInfo("""
    from typing import TypeVar, ParamSpec, Callable

    T = TypeVar("T")
    P = ParamSpec("P")

    def add(cls: Callable[P, T], *args: P.args, **kwargs: P.kwargs) -> T: ...

    class User:
        def __init__(self, name: str, age: int): ...

    add(User, )
    #         └ CARET
  """, "cls: (**P) -> T, name: str, age: int", "name: str, ")

  @Test
  @TestFor(classes = [PyParameterInfoHandler::class])
  fun `parameter info highlights the next forwarded parameter`() = assertParameterInfo("""
    from typing import Callable

    def add[**P, T](cls: Callable[P, T], *args: P.args, **kwargs: P.kwargs) -> T: ...

    class User:
        def __init__(self, name: str, age: int): ...

    add(User, "", )
    #             └ CARET
  """, "cls: (**P) -> T, name: str, age: int", "age: int")

  @Test
  @TestFor(classes = [PyParameterInfoHandler::class])
  fun `parameter info highlights the forwarded parameter of a keyword argument`() = assertParameterInfo("""
    from typing import Callable

    def add[**P, T](cls: Callable[P, T], *args: P.args, **kwargs: P.kwargs) -> T: ...

    class User:
        def __init__(self, name: str, age: int): ...

    add(User, age=1)
    #             └ CARET
  """, "cls: (**P) -> T, name: str, age: int", "age: int")

  @Test
  @TestFor(classes = [PyParameterInfoHandler::class])
  fun `parameter info shows the parameters of the function forwarded with a ParamSpec`() = assertParameterInfo("""
    from typing import Callable

    def add[**P, T](cls: Callable[P, T], *args: P.args, **kwargs: P.kwargs) -> T: ...

    def greet(greeting: str, *, loud: bool = False) -> str: ...

    add(greet, )
    #          └ CARET
  """, "cls: (**P) -> T, greeting: str, *, loud: bool = False", "greeting: str, ")

  @Test
  @TestFor(classes = [PyParameterInfoHandler::class])
  fun `parameter info shows the parameters forwarded by a method`() = assertParameterInfo("""
    from typing import Callable

    class Executor:
        def submit[**P, T](self, fn: Callable[P, T], *args: P.args, **kwargs: P.kwargs) -> T: ...

    def greet(greeting: str, *, loud: bool = False) -> str: ...

    Executor().submit(greet, )
    #                        └ CARET
  """, "fn: (**P) -> T, greeting: str, *, loud: bool = False", "greeting: str, ")

  @Test
  @TestFor(classes = [PyParameterInfoHandler::class])
  fun `parameter info shows the parameters forwarded by ThreadPoolExecutor submit`() = assertParameterInfo("""
    from concurrent.futures import ThreadPoolExecutor

    def greet(greeting: str, *, loud: bool = False) -> str: ...

    ThreadPoolExecutor().submit(greet, "Hello")
    #                                  └ CARET
  """, "fn: (**_P) -> _T, /, greeting: str, *, loud: bool = False", "greeting: str, ")

  @Test
  @TestFor(classes = [PyParameterInfoHandler::class])
  fun `parameter info shows one slash for a positional-only parameter forwarded by ThreadPoolExecutor submit`() = assertParameterInfo("""
    from concurrent.futures import ThreadPoolExecutor

    ThreadPoolExecutor().submit(len, )
    #                                └ CARET
  """, "fn: (**_P) -> _T, obj: Sized, /", "obj: Sized, ")

  @Test
  @TestFor(classes = [PyParameterInfoHandler::class])
  fun `parameter info shows the parameters forwarded with a Concatenate`() = assertParameterInfo("""
    from typing import Callable, Concatenate

    def call_with_int[**P, T](f: Callable[Concatenate[int, P], T], *args: P.args, **kwargs: P.kwargs) -> T: ...

    def describe(count: int, label: str, *, verbose: bool = False) -> str: ...

    call_with_int(describe, )
    #                       └ CARET
  """, "f: (Concatenate(int, **P)) -> T, label: str, *, verbose: bool = False", "label: str, ")

  @Test
  @TestFor(classes = [PyParameterInfoHandler::class])
  fun `parameter info shows the ParamSpec containers for an unsolved ParamSpec`() = assertParameterInfo("""
    from typing import Callable

    def add[**P, T](cls: Callable[P, T], *args: P.args, **kwargs: P.kwargs) -> T: ...

    add()
    #   └ CARET
  """, "cls: (**P) -> T, *args: **P, **kwargs: **P", "cls: (**P) -> T, ")

  @Test
  fun `keyword argument completion offers the forwarded parameters`() = assertKeywordArgumentsOffered("""
    from typing import Callable

    def add[**P, T](cls: Callable[P, T], *args: P.args, **kwargs: P.kwargs) -> T: ...

    class User:
        def __init__(self, name: str, age: int): ...

    add(User, )
    #         └ CARET
  """, "name=", "age=")

  @Test
  fun `keyword argument completion offers the parameters forwarded by a method`() = assertKeywordArgumentsOffered("""
    from typing import Callable

    class Executor:
        def submit[**P, T](self, fn: Callable[P, T], *args: P.args, **kwargs: P.kwargs) -> T: ...

    def greet(greeting: str, *, loud: bool = False) -> str: ...

    Executor().submit(greet, )
    #                        └ CARET
  """, "greeting=", "loud=")

  @Test
  fun `keyword argument completion offers the parameters forwarded by ThreadPoolExecutor submit`() = assertKeywordArgumentsOffered("""
    from concurrent.futures import ThreadPoolExecutor

    def greet(greeting: str, *, loud: bool = False) -> str: ...

    ThreadPoolExecutor().submit(greet, )
    #                                  └ CARET
  """, "greeting=", "loud=")

  @Test
  fun `keyword argument completion omits a positional-only parameter forwarded by ThreadPoolExecutor submit`() {
    val variants = completeKeywordArguments("""
      from concurrent.futures import ThreadPoolExecutor

      ThreadPoolExecutor().submit(len, )
      #                                └ CARET
    """)
    assertFalse("obj=" in variants, "Unexpected obj= in $variants")
  }

  private fun assertParameterInfo(@Language("Python") code: String, expectedText: String, vararg expectedHighlighted: String) {
    runInEdtAndWait {
      val file = configureWithCaret(code)
      PyParameterInfoTestCase.checkParameters(myFixture.caretOffset, file, expectedText, arrayOf(*expectedHighlighted), myFixture.editor)
    }
  }

  private fun assertKeywordArgumentsOffered(@Language("Python") code: String, vararg expected: String) {
    val variants = completeKeywordArguments(code)
    assertTrue(variants.containsAll(expected.asList()), "Expected ${expected.asList()} in $variants")
  }

  private fun completeKeywordArguments(@Language("Python") code: String): List<String> = runInEdtAndGet {
    configureWithCaret(code)
    myFixture.completeBasic()
    myFixture.lookupElementStrings.orEmpty()
  }
}

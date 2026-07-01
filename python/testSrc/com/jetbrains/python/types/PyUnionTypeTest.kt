// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components
import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for unions, `Optional`/`X | None`, `X | Y` bitwise-or unions,
 * `NoReturn`/`Never`, and operations whose subject is the union itself (attribute/call/subscription
 * access, iteration, slicing, flattening).
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyUnionTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class UnionInference {
    @Test
    fun `union of tuples returned from branches`() = test("""
      def x(b=True):
        if b:
          return (1, 'a')
        else:
          return ('a', 1)
      expr = x()
      # └ TYPE tuple[Literal[1], Literal['a']] | tuple[Literal['a'], Literal[1]]
      """)

    @Test
    fun `attribute initialised to None yields union with Any`() = test("""
      class C:
          def __init__(self): self.foo = None
      expr = C().foo
      # └ TYPE UnsafeUnion[None, Unknown]
      """)

    @Test
    fun `union with unknown type from unresolved call`() = test("""
      def f(c, x):
          if c:
              return 1
          return x
      expr = f(1, g())
      # │         └ ERROR Unresolved reference 'g'
      # └ TYPE Literal[1] | Unknown
      """)

    @Test
    fun `union iteration yields union of element types`() = test("""
      def f(c):
          if c < 0:
              return [1, 2, 3]
          elif c == 0:
              return 0.0
          else:
              return 'foo'
      
      def g(c):
          for expr in f(c):
      #       │       ^^^^ WARNING Expected type 'collections.Iterable', got 'list[int] | float | Literal["foo"]' instead
      #       └ TYPE int | LiteralString | Unknown
              pass
      """)

    @Test
    @TestFor(issues = ["PY-26973"])
    fun `slice of union picks matching member`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON36,
                  enableWeakWarnings = false,
                  assertRecursionPrevention = false),
      """
      from typing import Union
      myvar: Union[str, int]
      expr = myvar[0:3]
      #└ TYPE str
      """,
    )
  }

  @Nested
  inner class UnionMemberAccess {
    @Test
    fun `union member attribute of different types`() = test("""
      class Foo:
          x = []
      
      class Bar:
          x = 42
      
      def f(c):
          o = Foo() if c else Bar()
          expr = o.x
      #   └ TYPE list[Unknown] | int
      """)

    @Test
    @TestFor(issues = ["PY-11364"])
    fun `union member method call of different types`() = test("""
      class C1:
          def foo(self):
              return self
      
      class C2:
          def foo(self):
              return self
      
      def f():
          '''
          :rtype: C1 | C2
          '''
          pass
      
      expr = f().foo()
      # └ TYPE C1 | C2
      """)

    @Test
    @TestFor(issues = ["PY-12862"])
    fun `union member subscription of different types`() = test("""
      class C1:
          def __getitem__(self, item):
              return self
      
      class C2:
          def __getitem__(self, item):
              return self
      
      def f():
          '''
          :rtype: C1 | C2
          '''
          pass
      
      expr = f()[0]
      #└ TYPE C1 | C2
      print(expr)
      """)

    @Test
    fun `property of docstring union type`() = test("""
      def f():
          '''
          :rtype: int or slice
          '''
          raise NotImplementedError

      x = f()
      expr = x.bit_length()
      # │      ^^^^^^^^^^ WEAK-WARNING Member 'slice' of 'int | slice' does not have attribute 'bit_length'
      # └ TYPE int
      """)

    @Test
    fun `undefined property of union type`() = test("""
      x = 42 if True else 'spam'
      expr = x.foo
      #│       ^^^ WEAK-WARNING Member 'Literal[42]' of 'Literal[42, "spam"]' does not have attribute 'foo'
      #└ TYPE Unknown
      """)
  }

  @Nested
  inner class NoneAndOptionalInference {
    @Test
    fun `None parameter annotation`() = test("""
      def f(expr: None):
      #     └ TYPE None
          pass
      """)

    @Test
    fun `None return type`() = test("""
      def f() -> None:
          return 0
      #          └ WARNING Expected type 'None', got 'Literal[0]' instead
      expr = f()
      # └ TYPE None
      """)

    @Test
    fun `None literal`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON34, assertRecursionPrevention = false),
      """
      expr = None
      # └ TYPE None
      """,
    )

    @Test
    fun `type of None`() = test(
      TestOptions(assertRecursionPrevention = false), // PY-90413
      """
      expr = type(None)
      # └ TYPE type[None]
      """)

    @Test
    fun `Optional parameter annotation`() = test("""
      from typing import Optional
      
      def foo(expr: Optional[int]):
      #       └ TYPE int | None
          pass
      """)

    @Test
    @TestFor(issues = ["PY-28032"])
    fun `Optional of Any`() = test("""
      from typing import Optional, Any
      
      x = None  # type: Optional[Any]
      expr = x
      #└ TYPE Any | None
      """)

    @Test
    fun `Optional from default None`() = test("""
      def foo(expr: int = None):
      #       │           ^^^^ WARNING Expected type 'int', got 'None' instead
      #       └ TYPE int | None
          pass
      """)

    @Test
    fun `explicit None attribute`() = test("""
      class A:
          x: None
      
      def f(a: A):
          expr = a.x
      #   └ TYPE None
      """)
  }

  @Nested
  inner class UnionAnnotationsAndFlattening {
    @Test
    fun `Union annotation`() = test("""
      from typing import Union
      
      def f(expr: Union[int, str]):
      #     └ TYPE int | str
          pass
      """)

    @Test
    fun `nested Union annotations are flattened`() = test("""
      from typing import Union
      
      def foo(expr: Union[int, Union[str, list]]):
      #       └ TYPE int | str | list[Unknown]
          pass
      """)

    @Test
    fun `union of class object types`() = test("""
      from typing import Type, Union
      
      def f(x: Type[Union[int, str]]):
          expr = x
      #   └ TYPE type[int | str]
      """)

    @Test
    @TestFor(issues = ["PY-88281"])
    fun `union with partially unresolved member`() = test("""
      expr: int | asdf
      #│          ^^^^ ERROR Unresolved reference 'asdf'
      #└ TYPE int | Unknown
      """)
  }

  @Nested
  inner class BitwiseOrUnions {
    @Test
    @TestFor(issues = ["PY-44974"])
    fun `bitwise-or union from branches with from future import`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON39, assertRecursionPrevention = false),
      """
      from __future__ import annotations
      if something:
      #  ^^^^^^^^^ ERROR Unresolved reference 'something'
          x: int
      else:
          x: str
      expr = x
      # └ TYPE int | str
      """,
    )

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `bitwise-or union from branches without from future import`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON39, assertRecursionPrevention = false),
      """
      if something:
      #  ^^^^^^^^^ ERROR Unresolved reference 'something'
          x: int
      else:
          x: str
      expr = x
      #└ TYPE Union[int, str]
      """,
    )

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `parenthesized bitwise-or union of unions`() = test("""
      bar: int | ((list | dict) | (float | str)) = ""
      expr = bar
      #└ TYPE int | list[Unknown] | dict[Unknown, Unknown] | float | str
      """)

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `bitwise-or operator overload result`() = test("""
      class A:
        def __or__(self, other) -> int: return 5
      
      expr = A() | A()
      # └ TYPE int
      """)

    @Test
    fun `bitwise-or operator overload yields union`() = test("""
      class MyMeta(type):
          def __or__(self, other):
              return other
      
      class Foo(metaclass=MyMeta):
          ...
      
      expr = Foo | None
      # └ TYPE UnionType | Self
      """)

    @Test
    @TestFor(issues = ["PY-51329"])
    fun `bitwise-or operator overload union type alias`() = test("""
      from typing import Any
      
      class MyMeta(type):
          def __or__(self, other) -> Any:
              return other
      
      class Foo(metaclass=MyMeta):
          ...
      
      Alias = Foo | None
      expr: Alias
      #│    ^^^^^ WARNING Invalid type annotation
      #└ TYPE Unknown
      """)

    @Test
    @TestFor(issues = ["PY-51329"])
    fun `bitwise-or operator overload union type annotation`() = test("""
      from typing import Any
      
      class MyMeta(type):
          def __or__(self, other) -> Any:
              return other
      
      class Foo(metaclass=MyMeta):
          ...
      
      expr: Foo | None
      # │   ^^^^^^^^^^ WARNING Invalid type annotation
      # └ TYPE Unknown
      """)

    @Test
    fun `right-hand bitwise-or with class`() = test("""
      class M(type):
          def __ror__(self, other: object) -> int:
              return 1
      
      class A(metaclass=M): ...
      
      expr = str | A
      # └ TYPE UnionType | type[str] | int
      """)

    @Test
    fun `union with LiteralString collapses on string concatenation`() = test("""
      from typing import LiteralString
      
      x: LiteralString | str | int
      expr = x + "foo"
      #└ TYPE LiteralString FIXME LiteralString | str | Any # PY-90517
      """)
  }

  @Nested
  inner class NeverNoReturn {
    @Test
    fun `function that always raises is inferred as Never`() = test("""
      def f():
          raise Exception()
      
      expr = f()
      # └ TYPE Never
      """)
  }

  @Nested
  inner class MultiFile {
    @Test
    fun `None inside a callable alias from another file`() = test(
      """
      from other import MyType
      
      expr: MyType = ...
      # │            ^^^ WARNING Expected type '(int) -> None', got 'EllipsisType' instead
      # └ TYPE (int) -> None
      """,
      "other.py" to """
        from typing import Callable
        
        MyType = Callable[[int], None]
        """,
    )
  }

  @Nested
  inner class TypeCheckerInspectionsOnUnions {
    @Test
    fun `assigning list to None-int-str bitwise-or union is reported`() = test("""
      bar: None | int | str = [42] # WARNING Expected type 'None | int | str', got 'list[Literal[42]]' instead
      """)

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `assigning None to parenthesized bitwise-or union of unions is reported`() = test("""
      bar: int | ((list | dict) | (float | str)) = None # WARNING Expected type 'int | list[Unknown] | dict[Unknown, Unknown] | float | str', got 'None' instead
      """)

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `typing and types bitwise-or union difference`() = test("""
      from typing import Type
      def foo(x: Type[int | str]):
          pass
      foo(int | str) # WARNING Expected type 'type[int | str]', got 'UnionType | type[int] | type[str]' instead
      """)

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `bitwise-or unions and old-style unions are equivalent`() = test("""
      from typing import Union, Optional


      def expect_old_union(u: Union[int, str]):
          expect_new_union(u)
          expect_new_union(42)
          expect_new_union("42")
          expect_new_union([42]) # WARNING Expected type 'int | str', got 'list[Literal[42]]' instead


      def expect_new_union(u: int | str):
          expect_old_union(u)
          expect_old_union(42)
          expect_old_union("42")
          expect_old_union([42]) # WARNING Expected type 'int | str', got 'list[Literal[42]]' instead


      def expect_old_optional(u: Optional[int]):
          expect_new_optional_none_first(u)
          expect_new_optional_none_first(42)
          expect_new_optional_none_first(None)
          expect_new_optional_none_first([42]) # WARNING Expected type 'int | None', got 'list[Literal[42]]' instead
          expect_new_optional_none_last(u)
          expect_new_optional_none_last(42)
          expect_new_optional_none_last(None)
          expect_new_optional_none_last([42]) # WARNING Expected type 'int | None', got 'list[Literal[42]]' instead


      def expect_new_optional_none_first(u: None | int):
          expect_old_optional(u)
          expect_old_optional(42)
          expect_old_optional(None)
          expect_old_optional([42]) # WARNING Expected type 'int | None', got 'list[Literal[42]]' instead
          expect_new_optional_none_last(u)
          expect_new_optional_none_last(42)
          expect_new_optional_none_last(None)
          expect_new_optional_none_last([42]) # WARNING Expected type 'int | None', got 'list[Literal[42]]' instead


      def expect_new_optional_none_last(u: int | None):
          expect_old_optional(u)
          expect_old_optional(42)
          expect_old_optional(None)
          expect_old_optional([42]) # WARNING Expected type 'int | None', got 'list[Literal[42]]' instead
          expect_new_optional_none_first(u)
          expect_new_optional_none_first(42)
          expect_new_optional_none_first(None)
          expect_new_optional_none_first([42]) # WARNING Expected type 'int | None', got 'list[Literal[42]]' instead
      """)

    @Test
    fun `bitwise-or union with not calculated generic from union`() = test(
      TestOptions(enableWeakWarnings = false, assertRecursionPrevention = false),
      """
      from typing import Union, TypeVar

      T = TypeVar("T", bytes, str)

      my_union = Union[str, set[T]]
      another_union = Union[list[str], my_union[T]]


      def foo(path_or_buf: another_union[T] | None) -> None:
          print(path_or_buf)
      """,
    )
  }
}

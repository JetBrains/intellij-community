// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components
import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.LanguageLevel
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
      """.trimIndent())

    @Test
    fun `attribute initialised to None yields union with Any`() = test("""
      class C:
          def __init__(self): self.foo = None
      expr = C().foo
      # └ TYPE UnsafeUnion[None, Unknown]
      """.trimIndent())

    @Test
    fun `union with unknown type from unresolved call`() = test("""
      def f(c, x):
          if c:
              return 1
          return x
      expr = f(1, g())
      # │         └ ERROR Unresolved reference 'g'
      # └ TYPE Literal[1] | Unknown
      """.trimIndent())

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
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26973"])
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON36,
                enableWeakWarnings = false,
                assertRecursionPrevention = false)
    fun `slice of union picks matching member`() = test("""
      from typing import Union
      myvar: Union[str, int]
      expr = myvar[0:3]
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestInspections(disableInspections = [PyUnresolvedReferencesInspection::class])
    fun `union return types`() = test("""
      def test(c):
          def f1(c):
              if c < 0:
                  return []
              elif c > 0:
                  return 'foo'
              else:
                  return None
          def f2(x):
              '''
              :type x: str
              '''
              pass
          def f3(x):
              '''
              :type x: int
              '''
          x1 = f1(c)
          f2(x1)
      #      ^^ WARNING Expected type 'str', got 'list[Unknown] | Literal["foo"] | None' instead
          f3(x1)
      #      ^^ WARNING Expected type 'int', got 'list[Unknown] | Literal["foo"] | None' instead

          f2(x1.count(''))
      #      ^^^^^^^^^^^^ WARNING Expected type 'str', got 'int | Unknown' instead
          f3(x1.count(''))
          f2(x1.strip())
          f3(x1.strip())
      #      ^^^^^^^^^^ WARNING Expected type 'int', got 'LiteralString | Unknown' instead
      """.trimIndent())
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
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-90791"])
    fun `union member generic attribute`() = test("""
      class Box[T]:
          t: T

      def foo(box: Box[int] | Box[str]):
          expr = box.t
      #   └ TYPE int | str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-90791"])
    fun `union member generic descriptor`() = test("""
      class Field[T]:
          def __get__(self, instance, owner) -> T:
              raise NotImplementedError

      class Box[T]:
          t: Field[T]

      def foo(box: Box[int] | Box[str]):
          expr = box.t
      #   └ TYPE int | str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-90791", "PY-91007"])
    fun `union member generic property`() = test("""
      class Box[T]:
          @property
          def t(self) -> T:
              raise NotImplementedError

      def foo(box: Box[int] | Box[str]):
          expr = box.t
      #   └ TYPE int | str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-90791", "PY-90894"])
    fun `union member generic __getattr__`() = test("""
      class Box[T]:
          def __getattr__(self, item) -> T:
              raise NotImplementedError

      def foo(box: Box[int] | Box[str]):
          expr = box.whatever
      #   └ TYPE int | str
      """.trimIndent())

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
      """.trimIndent())

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
      """.trimIndent())

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
      # └ TYPE int | Unknown
      """.trimIndent())

    @Test
    fun `undefined property of union type`() = test("""
      x = 42 if True else 'spam'
      expr = x.foo
      #│       ^^^ WEAK-WARNING Member 'Literal[42]' of 'Literal[42, "spam"]' does not have attribute 'foo'
      #└ TYPE Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-8182"])
    fun `union with same methods`() = test("""
      class C:
          def g(self, x):
              '''
              :type x: int
              '''
              pass

          def method_c(self):
              pass

      class D:
          def g(self, x):
              '''
              :type x: list
              '''
              pass

          def method_d(self):
              pass

      def f():
          '''
          :rtype: C or D
          '''
          pass

      obj = f()
      obj.g(10)
      #     ^^ WARNING Expected type 'list', got 'Literal[10]' instead
      obj.g([])
      #     ^^ WARNING Expected type 'int', got 'list[Unknown]' instead
      """.trimIndent())
  }

  @Nested
  inner class NoneAndOptionalInference {
    @Test
    fun `None parameter annotation`() = test("""
      def f(expr: None):
      #     └ TYPE None
          pass
      """.trimIndent())

    @Test
    fun `None return type`() = test("""
      def f() -> None:
          return 0
      #          └ WARNING Expected type 'None', got 'Literal[0]' instead
      expr = f()
      # └ TYPE None
      """.trimIndent())

    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON34, assertRecursionPrevention = false)
    fun `None literal`() = test("""
      expr = None
      # └ TYPE None
      """.trimIndent())

    @Test
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `type of None`() = test(
      // PY-90413
      """
      expr = type(None)
      # └ TYPE type[None]
      """.trimIndent())

    @Test
    fun `Optional parameter annotation`() = test("""
      from typing import Optional
      
      def foo(expr: Optional[int]):
      #       └ TYPE int | None
          pass
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-28032"])
    fun `Optional of Any`() = test("""
      from typing import Optional, Any
      
      x = None  # type: Optional[Any]
      expr = x
      #└ TYPE Any | None
      """.trimIndent())

    @Test
    fun `Optional from default None`() = test("""
      def foo(expr: int = None):
      #       │           ^^^^ WARNING Expected type 'int', got 'None' instead
      #       └ TYPE int | None
          pass
      """.trimIndent())

    @Test
    fun `explicit None attribute`() = test("""
      class A:
          x: None
      
      def f(a: A):
          expr = a.x
      #   └ TYPE None
      """.trimIndent())
  }

  @Nested
  inner class UnionAnnotationsAndFlattening {
    @Test
    fun `Union annotation`() = test("""
      from typing import Union
      
      def f(expr: Union[int, str]):
      #     └ TYPE int | str
          pass
      """.trimIndent())

    @Test
    fun `nested Union annotations are flattened`() = test("""
      from typing import Union
      
      def foo(expr: Union[int, Union[str, list]]):
      #       └ TYPE int | str | list[Unknown]
          pass
      """.trimIndent())

    @Test
    fun `union of class object types`() = test("""
      from typing import Type, Union
      
      def f(x: Type[Union[int, str]]):
          expr = x
      #   └ TYPE type[int | str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-88281"])
    fun `union with partially unresolved member`() = test("""
      expr: int | asdf
      #│          ^^^^ ERROR Unresolved reference 'asdf'
      #└ TYPE int | Unknown
      """.trimIndent())
  }

  @Nested
  inner class BitwiseOrUnions {
    @Test
    @TestFor(issues = ["PY-44974"])
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON39, assertRecursionPrevention = false)
    fun `bitwise-or union from branches with from future import`() = test("""
      from __future__ import annotations
      if something:
      #  ^^^^^^^^^ ERROR Unresolved reference 'something'
          x: int
      else:
          x: str
      expr = x
      # └ TYPE int | str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-44974"])
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON39, assertRecursionPrevention = false)
    fun `bitwise-or union from branches without from future import`() = test("""
      if something:
      #  ^^^^^^^^^ ERROR Unresolved reference 'something'
          x: int
      else:
          x: str
      expr = x
      #└ TYPE Union[int, str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `parenthesized bitwise-or union of unions`() = test("""
      bar: int | ((list | dict) | (float | str)) = ""
      expr = bar
      #└ TYPE int | list[Unknown] | dict[Unknown, Unknown] | float | str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `bitwise-or operator overload result`() = test("""
      class A:
        def __or__(self, other) -> int: return 5
      
      expr = A() | A()
      # └ TYPE int
      """.trimIndent())

    @Test
    fun `bitwise-or operator overload yields union`() = test("""
      class MyMeta(type):
          def __or__(self, other):
              return other
      
      class Foo(metaclass=MyMeta):
          ...
      
      expr = Foo | None
      # └ TYPE UnionType | Self
      """.trimIndent())

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
      """.trimIndent())

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
      """.trimIndent())

    @Test
    fun `right-hand bitwise-or with class`() = test("""
      class M(type):
          def __ror__(self, other: object) -> int:
              return 1
      
      class A(metaclass=M): ...
      
      expr = str | A
      # └ TYPE UnionType | type[str] | int
      """.trimIndent())

    @Test
    fun `union with LiteralString collapses on string concatenation`() = test("""
      from typing import LiteralString
      
      x: LiteralString | str | int
      expr = x + "foo"
      #│       └ WARNING '+' is not supported between 'int' and 'Literal["foo"]'
      #└ TYPE LiteralString FIXME LiteralString | str | Any # PY-90517
      """.trimIndent())
  }

  @Nested
  inner class NeverNoReturn {
    @Test
    fun `function that always raises is inferred as Never`() = test("""
      def f():
          raise Exception()
      
      expr = f()
      # └ TYPE Never
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-5873"])
    fun `type of raise exception`() = test("""
      def test():
          def f1(x):
              '''
              :type x: int
              '''
              pass

          class C:
              def f(self):
                  raise NotImplementedError()

          x = C()
          f1(x.f())
      """.trimIndent())
  }

  @Nested
  inner class MultiFile {
    @Test
    fun `None inside a callable alias from another file`() = test("""
      from other import MyType
      
      expr: MyType = ...
      # │            ^^^ WARNING Expected type '(int) -> None', got 'EllipsisType' instead
      # └ TYPE (int) -> None
      """.trimIndent(),
      "other.py" to """
        from typing import Callable
        
        MyType = Callable[[int], None]
        """.trimIndent())
  }

  @Nested
  inner class TypeCheckerInspectionsOnUnions {
    @Test
    fun `assigning list to None-int-str bitwise-or union is reported`() = test("""
      bar: None | int | str = [42] # WARNING Expected type 'None | int | str', got 'list[Literal[42]]' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `assigning None to parenthesized bitwise-or union of unions is reported`() = test("""
      bar: int | ((list | dict) | (float | str)) = None # WARNING Expected type 'int | list[Unknown] | dict[Unknown, Unknown] | float | str', got 'None' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `typing and types bitwise-or union difference`() = test("""
      from typing import Type
      def foo(x: Type[int | str]):
          pass
      foo(int | str) # WARNING Expected type 'type[int | str]', got 'UnionType | type[int] | type[str]' instead
      """.trimIndent())

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
      """.trimIndent())

    @Test
    @TestCaseOptions(enableWeakWarnings = false, assertRecursionPrevention = false)
    fun `bitwise-or union with not calculated generic from union`() = test("""
      from typing import Union, TypeVar

      T = TypeVar("T", bytes, str)

      my_union = Union[str, set[T]]
      another_union = Union[list[str], my_union[T]]


      def foo(path_or_buf: another_union[T] | None) -> None:
          print(path_or_buf)
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-20364"])
    fun `actual basestring expected union str unicode`() = test("""
      def hello(filename):
          '''
          :type filename: basestring
          '''
          open(filename)
      """.trimIndent())
  }

  @Nested
  inner class StrictUnionOperators {
    @Test
    @TestFor(issues = ["PY-90532"])
    fun `binary operator member rejects the operand`() = test("""
      class LeftOperand:
          pass

      class RightOperand:
          pass

      class AddsLeftOperand:
          def __add__(self, other: LeftOperand) -> "AddsLeftOperand": ...

      class AddsRightOperand:
          def __add__(self, other: RightOperand) -> "AddsRightOperand": ...

      def f(x: AddsLeftOperand | AddsRightOperand, arg: LeftOperand):
          _ = x + arg
      #         └ WARNING '+' is not supported between 'AddsRightOperand' and 'LeftOperand'
      """)

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `binary operator generic member rejects the operand`() = test("""
      from typing import Generic, TypeVar

      class LeftPayload:
          pass

      class RightPayload:
          pass

      T = TypeVar("T")

      class Box(Generic[T]):
          def __add__(self, other: "Box[T]") -> "Box[T]": ...

      def f(x: Box[LeftPayload] | Box[RightPayload], y: Box[LeftPayload]):
          _ = x + y
      #         └ WARNING '+' is not supported between 'Box[RightPayload]' and 'Box[LeftPayload]'
      """)

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `in operator member rejects the operand`() = test("""
      class LeftItem:
          pass

      class RightItem:
          pass

      class LeftContainer:
          def __contains__(self, item: LeftItem) -> bool: return True

      class RightContainer:
          def __contains__(self, item: RightItem) -> bool: return True

      def f(c: LeftContainer | RightContainer, x: LeftItem):
          _ = x in c
      #         ^^ WARNING 'in' is not supported between 'LeftItem' and 'RightContainer'
      """)

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `augmented assignment operator rejects the operand`() = test("""
      class GoodValue:
          pass

      class BadValue:
          pass

      class IaddGood:
          def __iadd__(self, other: GoodValue):
              return self

      class AlsoIaddGood:
          def __iadd__(self, other: GoodValue):
              return self

      def f(x: IaddGood | AlsoIaddGood, v: BadValue):
          x += v
      #     ^^ WARNING '+=' is not supported between 'IaddGood | AlsoIaddGood' and 'BadValue'
      """)

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `binary operator accepted by every member`() = test("""
      class Operand:
          pass

      class AddsOperand:
          def __add__(self, other: Operand) -> "AddsOperand": ...

      class AlsoAddsOperand:
          def __add__(self, other: Operand) -> "AlsoAddsOperand": ...

      def f(x: AddsOperand | AlsoAddsOperand, y: Operand):
          _ = x + y
      """)

    // A member whose own operator rejects the operand is still fine if the operand's reflected operator takes it.
    @Test
    @TestFor(issues = ["PY-90532"])
    fun `binary operator member supported by the reflected operator`() = test("""
      class Rhs:
          def __radd__(self, other: "Strict") -> "Rhs": ...

      class Lenient:
          def __add__(self, other: Rhs) -> "Lenient": ...

      class Strict:
          def __add__(self, other: "Strict") -> "Strict": ...

      def f(x: Lenient | Strict, y: Rhs):
          _ = x + y
      """)

    // Only the operand types that no member handles are reported, not the whole type of the other operand.
    @Test
    @TestFor(issues = ["PY-90532"])
    fun `binary operator reports only the unhandled operand types`() = test("""
      class First:
          pass

      class Second:
          pass

      class AcceptsBoth:
          def __add__(self, other: First | Second) -> "AcceptsBoth": ...

      class AcceptsFirst:
          def __add__(self, other: First) -> "AcceptsFirst": ...

      def f(x: AcceptsBoth | AcceptsFirst, y: First | Second):
          _ = x + y
      #         └ WARNING '+' is not supported between 'AcceptsFirst' and 'Second'
      """)

    // The reflected operator only has to cover the member/operand combinations that the member itself rejects:
    // `AcceptsFirst + Second` is handled by `Second.__radd__`, and `AcceptsFirst + First` by `AcceptsFirst.__add__`.
    @Test
    @TestFor(issues = ["PY-90532"])
    fun `binary operator operand union covered per combination`() = test("""
      class First:
          pass

      class Second:
          def __radd__(self, other: "AcceptsFirst") -> "Second": ...

      class AcceptsBoth:
          def __add__(self, other: First | Second) -> "AcceptsBoth": ...

      class AcceptsFirst:
          def __add__(self, other: First) -> "AcceptsFirst": ...

      def f(x: AcceptsBoth | AcceptsFirst, y: First | Second):
          _ = x + y
      """)

    // The union is the right operand: `Lhs.__add__` rejects `NoRadd`, and `NoRadd` has no reflected operator to fall
    // back to. `HasRadd.__radd__` covers only the other member, so it must not mask the mismatch.
    @Test
    @TestFor(issues = ["PY-90532"])
    fun `binary operator operand union member rejected by the left operator`() = test("""
      class Lhs:
          def __add__(self, other: "HasRadd") -> "Lhs": ...

      class HasRadd:
          def __radd__(self, other: Lhs) -> "HasRadd": ...

      class NoRadd:
          pass

      def f(lhs: Lhs, rhs: HasRadd | NoRadd):
          _ = lhs + rhs
      #           └ WARNING '+' is not supported between 'Lhs' and 'NoRadd'
      """)

    // Same as above for the in-place operator: `Lhs.__iadd__` accepts only one member of the right-hand union.
    @Test
    @TestFor(issues = ["PY-90532"])
    fun `augmented assignment operand union member rejected by the inplace operator`() = test("""
      class Lhs:
          def __iadd__(self, other: "HasRadd") -> "Lhs": ...

      class HasRadd:
          def __radd__(self, other: Lhs) -> "HasRadd": ...

      class NoRadd:
          pass

      def f(lhs: Lhs, rhs: HasRadd | NoRadd):
          lhs += rhs
      #       ^^ WARNING '+=' is not supported between 'Lhs' and 'NoRadd'
      """)

    // Every member of the right-hand union is either accepted by `__iadd__` or defines its own reflected operator.
    @Test
    @TestFor(issues = ["PY-90532"])
    fun `augmented assignment operand union covered by the reflected operators`() = test("""
      class Lhs:
          def __iadd__(self, other: "HasRadd") -> "Lhs": ...

      class HasRadd:
          def __radd__(self, other: Lhs) -> "HasRadd": ...

      class AlsoHasRadd:
          def __radd__(self, other: Lhs) -> "AlsoHasRadd": ...

      def f(lhs: Lhs, rhs: HasRadd | AlsoHasRadd):
          lhs += rhs
      """)

    @Test
    @TestFor(issues = ["PY-89978"])
    fun `augmented assignment no false positive for float int union`() = test("""
      def f(foo: float | int):
          foo += 1
      """)

    @Test
    @TestFor(issues = ["PY-89798"])
    fun `augmented assignment on multiple local variables no false positive`() = test("""
      def foo() -> None:
          left, right = 0, 42

          while left < right:
              left += 1
              right -= 1
      """)

    @Test
    @TestFor(issues = ["PY-90475"])
    fun `augmented assignment on local variable no false positive`() = test("""
      def bar(a: int, b: int):
          foo = 2

          if a > 0:
              foo += 1

          if b > 0:
              foo -= 1
      """)

    @Test
    @TestFor(issues = ["PY-89978"])
    fun `augmented assignment operator missing on every member including None`() = test("""
      class Empty:
          pass

      class Rhs:
          pass

      def f(x: Empty | None):
          x += Rhs()
      #     ^^ WARNING '+=' is not supported between 'Empty | None' and 'Rhs'
      #     ^^ WEAK-WARNING Member 'Empty' of 'Empty | None' does not have attribute '__iadd__'
      """)

    @Test
    @TestFor(issues = ["PY-89978"])
    fun `binary operator missing on every member including None`() = test("""
      class Empty:
          pass

      class Rhs:
          pass

      def f(x: Empty | None):
          _ = x + Rhs()
      #         └ WARNING '+' is not supported between 'Empty | None' and 'Rhs'
      #         └ WEAK-WARNING Member 'Empty' of 'Empty | None' does not have attribute '__add__'
      """)

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `binary operator missing on every member`() = test("""
      class Empty:
          pass

      class AlsoEmpty:
          pass

      class Rhs:
          pass

      def f(x: Empty | AlsoEmpty, y: Rhs):
          _ = x + y
      #         └ WARNING '+' is not supported between 'Empty | AlsoEmpty' and 'Rhs'
      #         └ WEAK-WARNING Member 'Empty' of 'Empty | AlsoEmpty' does not have attribute '__add__' FIXME # duplicates the type checker warning
      """)

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `augmented assignment operator missing on every member`() = test("""
      class Empty:
          pass

      class AlsoEmpty:
          pass

      class Rhs:
          pass

      def f(x: Empty | AlsoEmpty, y: Rhs):
          x += y
      #     ^^ WARNING '+=' is not supported between 'Empty | AlsoEmpty' and 'Rhs'
      #     ^^ WEAK-WARNING Member 'Empty' of 'Empty | AlsoEmpty' does not have attribute '__iadd__' FIXME # duplicates the type checker warning
      """)

    @Test
    @TestFor(issues = ["PY-85880", "PY-90532"])
    fun `in operator with None member of the container union`() = test("""
      from typing import Literal


      def f(e: Literal[1, 2]):
          a: tuple | None = None
          _ = e in a
      #         ^^ WARNING 'in' is not supported between 'Literal[1, 2]' and 'None'
      """)

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `strict-union operator ignores Any member but still validates others`() = test("""
      from typing import Any

      class AddsOperand:
          def __add__(self, other: Operand) -> Any:
              pass

      class Operand:
          pass

      class Other:
          pass

      def f(x: AddsOperand | Any, y: Operand, z: Other):
          _ = x + y
          _ = x + z
      #         └ WARNING '+' is not supported between 'AddsOperand' and 'Other'
      """)

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `in and not-in operators are validated the same way`() = test("""
      class Elem:
          pass

      class Box:
          def __contains__(self, item: Elem) -> bool: ...

      class NoBox:
          pass

      def f(x: Elem, y: Box | NoBox):
          _ = x in y
      #         ^^ WARNING 'in' is not supported between 'Elem' and 'NoBox'
          _ = x not in y
      #         ^^^ WARNING 'not in' is not supported between 'Elem' and 'NoBox'
      """)

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `strict-union operator on a member with the wrong arity is still reported`() = test("""
      class Operand:
          pass

      class L1:
          def __add__(self, other: Operand, extra: int) -> "L1": ...

      class L2:
          def __add__(self, other: Operand) -> "L2": ...

      def f(x: L1 | L2, y: Operand):
          _ = x + y
      #         └ WARNING '+' is not supported between 'L1' and 'Operand'
      """)

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `strict-union operator on a member accepting no operand argument is still reported`() = test("""
      class Operand:
          pass

      class L1:
          def __add__(self) -> "L1": ...

      class L2:
          def __add__(self, other: Operand) -> "L2": ...

      def f(x: L1 | L2, y: Operand):
          _ = x + y
      #         └ WARNING '+' is not supported between 'L1' and 'Operand'
      """)
  }

  /**
   * Unlike a strict union, an unsafe union or an intersection supports an operator as soon as *one* of its members
   * does: an unsafe union is a subtype of anything one of its members is a subtype of, and an intersection value is
   * a value of each of its members.
   */
  @Nested
  inner class UnsafeUnionAndIntersectionOperators {
    private fun unsafeUnionOverloads(firstType: String, secondType: String): Pair<String, String> = "unsafe_union.py" to """
      from typing import overload
      from aaa import $firstType, $secondType

      @overload
      def make_unsafe_union(flag: int) -> $firstType: ...
      @overload
      def make_unsafe_union(flag: str) -> $secondType: ...
      def make_unsafe_union(flag): ...
      """.trimIndent()

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `unsafe union member supporting the operator is enough`() = test(
      """
      class Operand:
          pass

      class AddsOperand:
          def __add__(self, other: Operand) -> "AddsOperand": ...

      class NoAdd:
          pass

      from unsafe_union import make_unsafe_union

      def f(y: Operand):
          x = make_unsafe_union()
      #   │                     └ WARNING No overload of 'make_unsafe_union' matches the arguments. Argument types: (). Expected one of: (flag: int), (flag: str)
      #   └ TYPE UnsafeUnion[AddsOperand, NoAdd]
          _ = x + y
      """,
      unsafeUnionOverloads("AddsOperand", "NoAdd"),
    )

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `strict-union operator accepts an unsafe-union operand covered by every member`() = test(
      """
      class Accepted:
          pass

      class Rejected:
          pass

      class L1:
          def __add__(self, other: Accepted) -> "L1": ...

      class L2:
          def __add__(self, other: Accepted) -> "L2": ...

      from unsafe_union import make_unsafe_union

      def f(lhs: L1 | L2):
          rhs = make_unsafe_union()
      #   │                       └ WARNING No overload of 'make_unsafe_union' matches the arguments. Argument types: (). Expected one of: (flag: int), (flag: str)
      #   └ TYPE UnsafeUnion[Accepted, Rejected]
          _ = lhs + rhs
      """,
      unsafeUnionOverloads("Accepted", "Rejected"),
    )

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `unsafe union rejecting the operand on every member is reported`() = test(
      """
      class Operand:
          pass

      class Other:
          pass

      class AddsOperand:
          def __add__(self, other: Operand) -> "AddsOperand": ...

      class AlsoAddsOperand:
          def __add__(self, other: Operand) -> "AlsoAddsOperand": ...

      from unsafe_union import make_unsafe_union

      def f(y: Other):
          x = make_unsafe_union()
      #   │                     └ WARNING No overload of 'make_unsafe_union' matches the arguments. Argument types: (). Expected one of: (flag: int), (flag: str)
      #   └ TYPE UnsafeUnion[AddsOperand, AlsoAddsOperand]
          _ = x + y
      #           └ WARNING No overload of '__add__' matches the arguments. Argument types: (Other). Expected one of: (other: Operand), (other: Operand)
      """,
      unsafeUnionOverloads("AddsOperand", "AlsoAddsOperand"),
    )

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `unsafe union as the right operand accepted by one member`() = test(
      """
      class Lhs:
          def __add__(self, other: "Accepted") -> "Lhs": ...

      class Accepted:
          pass

      class Rejected:
          pass

      from unsafe_union import make_unsafe_union

      def f(lhs: Lhs):
          rhs = make_unsafe_union()
      #   │                       └ WARNING No overload of 'make_unsafe_union' matches the arguments. Argument types: (). Expected one of: (flag: int), (flag: str)
      #   └ TYPE UnsafeUnion[Accepted, Rejected]
          _ = lhs + rhs
      """,
      unsafeUnionOverloads("Accepted", "Rejected"),
    )

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `intersection operator in a type hint is an undefined operator`() = test("""
      expr: int & str
      #         └ WARNING Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances
      """)

    // An intersection value is a value of each member, so the operator of any single member applies.
    @Test
    @TestFor(issues = ["PY-90532"])
    fun `intersection member defining the operator is enough`() = test("""
      class Operand:
          pass

      class NoAdd:
          pass

      class AddsOperand:
          def __add__(self, other: Operand) -> "AddsOperand": ...

      def f(x: NoAdd, y: Operand):
          if isinstance(x, AddsOperand):
              expr = x
      #       └ TYPE NoAdd & AddsOperand
              _ = x + y
      """)

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `intersection rejecting the operand on every member is reported`() = test("""
      class Operand:
          pass

      class Other:
          pass

      class AddsOperand:
          def __add__(self, other: Operand) -> "AddsOperand": ...

      class AlsoAddsOperand:
          def __add__(self, other: Operand) -> "AlsoAddsOperand": ...

      def f(x: AddsOperand, y: Other):
          if isinstance(x, AlsoAddsOperand):
              expr = x
      #       └ TYPE AddsOperand & AlsoAddsOperand
              _ = x + y
      #               └ WARNING No overload of '__add__' matches the arguments. Argument types: (Other). Expected one of: (other: Operand), (other: Operand)
      """)

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `intersection as the right operand supported by the reflected operator`() = test("""
      class Lhs:
          def __add__(self, other: "Accepted") -> "Lhs": ...

      class Accepted:
          pass

      class HasRadd:
          def __radd__(self, other: Lhs) -> "HasRadd": ...

      class NoRadd:
          pass

      def f(lhs: Lhs, rhs: NoRadd):
          if isinstance(rhs, HasRadd):
              expr = rhs
      #       └ TYPE NoRadd & HasRadd
              _ = lhs + rhs
      """)

    @Test
    @TestFor(issues = ["PY-90532"])
    fun `intersection member accepting the whole right-hand union is enough`() = test("""
      class Operand:
          pass

      class Other:
          pass

      class Base:
          pass

      class AddsBoth:
          def __add__(self, other: Operand | Other) -> "AddsBoth": ...

      def f(x: Base, y: Operand | Other):
          if isinstance(x, AddsBoth):
              expr = x
      #       └ TYPE Base & AddsBoth
              _ = x + y
      """)
  }
}

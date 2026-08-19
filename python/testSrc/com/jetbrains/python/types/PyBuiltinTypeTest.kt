// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components
import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type tests for inference around builtins, the standard library and docstring-derived types:
 * builtin functions (`open`, `input`, `min`/`max`/`sum`, `round`, `float.fromhex`),
 * dict builtin method results, `collections` types, binary/unary/augmented operator result types,
 * and types inferred from docstrings (`:type:`/`:rtype:`, numpy/google docstrings, `# type:` comments).
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyBuiltinTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class OpenAndIoOpen {
    @Test
    fun `open default mode`() = test("""
      expr = open('foo')
      # └ TYPE TextIOWrapper[_WrappedBuffer]
      """.trimIndent())

    @Test
    fun `open text mode`() = test("""
      expr = open('foo', 'r')
      # └ TYPE TextIOWrapper[_WrappedBuffer]
      """.trimIndent())

    @Test
    fun `open binary mode`() = test("""
      expr = open('foo', 'rb')
      #└ TYPE BufferedReader[_BufferedReaderStream]
      """.trimIndent())

    @Test
    fun `io open default mode`() = test("""
      import io
      expr = io.open('foo')
      # └ TYPE TextIOWrapper[_WrappedBuffer]
      """.trimIndent())

    @Test
    fun `io open text mode`() = test("""
      import io
      expr = io.open('foo', 'r')
      #└ TYPE TextIOWrapper[_WrappedBuffer]
      """.trimIndent())

    @Test
    fun `io open binary mode`() = test("""
      import io
      expr = io.open('foo', 'rb')
      #└ TYPE BufferedReader[_BufferedReaderStream]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-7757"])
    fun `open read 2k`() = test("""
      def f(s):
          '''
          :type s: str
          '''
          pass

      def g(s):
          '''
          :type s: int
          '''

      f(open('foo').read()) # pass
      g(open('foo').read())
      # ^^^^^^^^^^^^^^^^^^ WARNING Expected type 'int', got 'str' instead
      """.trimIndent())
  }

  @Nested
  inner class Input {
    @Test
    @TestFor(issues = ["PY-21350"])
    fun `input result`() = test("""
      expr = input()
      #└ TYPE str
      """.trimIndent())

  }

  @Nested
  inner class MinMaxSum {
    @Test
    fun `min result`() = test("""
      expr = min(1, 2, 3)
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `max result`() = test("""
      expr = max(1, 2, 3)
      # └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-21692"])
    fun `sum result`() = test("""
      expr = sum([1, 2, 3])
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-20757"])
    fun `min else None`() = test("""
      def get_value(v):
          if v:
              return min(v)
          else:
              return None
      expr = get_value([])
      #└ TYPE SupportsDunderLT[Any] | SupportsDunderGT[Any] | None
      """.trimIndent())
  }

  @Nested
  inner class Round {
    @Test
    @TestFor(issues = ["PY-13750"])
    fun `round of int without ndigits`() = test("""
      expr = round(1)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `round of int with ndigits`() = test("""
      expr = round(1, 1)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `round of float without ndigits`() = test("""
      expr = round(1.1)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `round of float with ndigits`() = test("""
      expr = round(1.1, 1)
      #└ TYPE float | int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `round of bool without ndigits`() = test("""
      expr = round(True)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `round of bool with ndigits`() = test("""
      expr = round(True, 1)
      #└ TYPE int
      """.trimIndent())
  }

  @Nested
  inner class FloatFromhex {
    @Test
    @TestFor(issues = ["PY-21083"])
    fun `float fromhex`() = test("""
      expr = float.fromhex("0.5")
      #└ TYPE float
      """.trimIndent())
  }

  @Nested
  inner class LiteralsBytesFStringListOfClass {
    @Test
    @TestFor(issues = ["PY-1427"])
    fun `bytes literal`() = test("""
      expr = b'foo'
      #└ TYPE bytes
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-29665"])
    fun `raw bytes literal rb`() = test("""
      expr = rb'raw bytes'
      #└ TYPE bytes
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-29665"])
    fun `raw bytes literal br`() = test("""
      expr = br'raw bytes'
      #└ TYPE bytes
      """.trimIndent())

    @Test
    fun `f-string literal type`() = test("""
      expr = f'foo'
      #└ TYPE str
      """.trimIndent())

    @Test
    fun `list literal of class object`() = test("""
      expr = [float]
      #└ TYPE list[type[float]]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-10095"])
    fun `string starts with`() = test("""
      'foo'.startswith('bar')
      'foo'.startswith(('bar', 'baz'))
      'foo'.startswith(2)
      #                └ WARNING Expected type 'str | tuple[str, ...]', got 'Literal[2]' instead

      u'foo'.startswith(u'bar')
      u'foo'.startswith((u'bar', u'baz'))
      u'foo'.startswith(2)
      #                 └ WARNING Expected type 'str | tuple[str, ...]', got 'Literal[2]' instead
      """.trimIndent())
  }

  @Nested
  inner class CollectionsStdlibTypes {
    @Test
    fun `defaultdict from dict`() = test("""
      from collections import defaultdict
      expr = defaultdict(dict)
      #└ TYPE defaultdict[Unknown, dict]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-19884"])
    fun `abs set and mutable set`() = test("""
      def f(xs, ys):
          '''
          :type xs: collections.Set[int]
          :type ys: collections.MutableSet[int]
          '''
          pass

      items = {4, 5}
      f({1, 2, 3}, items)
      """.trimIndent())
  }

  @Nested
  inner class DictBuiltinMethodResults {
    @Test
    @TestFor(issues = ["PY-20409"])
    fun `get from dict with default None value`() = test("""
      d = {}
      expr = d.get("abc", None)
      #└ TYPE Unknown | None
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-82818"])
    fun `pop from dict with default None value`() = test("""
      d = {}
      expr = d.pop("abc", None)
      #└ TYPE Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-83704"])
    fun `pop from typed dict with default None value`() = test("""
      d: dict[str, int] = {"abc": 0, "1": 1}
      expr = d.pop("abc", None)
      #└ TYPE int | None
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-83704"])
    fun `pop from Any-valued dict with default None value`() = test("""
      from typing import Any
      d: dict[str, Any] = {"abc": "s", "1": 1}
      expr = d.pop("abc", None)
      #└ TYPE Any
      """.trimIndent())
  }

  @Nested
  inner class DocstringDerivedTypesReStructuredText {
    @Test
    fun `type from method call comment`() = test("""
      expr = ''.capitalize()
      #└ TYPE LiteralString
      """.trimIndent())

    @Test
    fun `rest param type`() = test("""
      def foo(limit):
        ''':param integer limit: maximum number of stack frames to show'''
        expr = limit
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-3849"])
    fun `rest class type`() = test("""
      class Foo: pass
      def foo(limit):
        ''':param :class:`Foo` limit: maximum number of stack frames to show'''
        expr = limit
      #   └ TYPE Foo
      """.trimIndent())

    @Test
    fun `rest ivar type`() = test("""
      def foo(p):
          var = p.bar
          ''':type var: str'''
          expr = var
      #   └ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-6584"])
    fun `class attribute type in class docstring via class`() = test("""
      class C(object):
          '''
          :type foo: int
          '''
          foo = None
      
      expr = C.foo
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-6584"])
    fun `class attribute type in class docstring via instance`() = test("""
      class C(object):
          '''
          :type foo: int
          '''
          foo = None
      
      expr = C().foo
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-6584"])
    fun `instance attribute type in class docstring`() = test("""
      class C(object):
          '''
          :type foo: int
          '''
          def __init__(self, bar):
              self.foo = bar
      
      def f(x):
          expr = C(x).foo
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-8953"])
    fun `self type in docstring`() = test("""
      class C(object):
          def foo(self):
              '''
              :type self: int
              '''
              expr = self
      #       └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-7322"])
    fun `namedtuple parameter type in docstring`() = test("""
      from collections import namedtuple
      Point = namedtuple('Point', ('x', 'y'))
      def takes_a_point(point):
          '''
          :type point: Point
          '''
          expr = point
      #   └ TYPE Point
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-4813"])
    fun `parameter type inference in subclass from docstring`() = test("""
      class Base:
          def test(self, param):
              '''
              :param param:
              :type param: int
              '''
              pass
      
      class Subclass(Base):
          def test(self, param):
              expr = param
      #       └ TYPE int
      """.trimIndent())

    @Test
    fun simple() = test("""
      def f1(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10=10, p11='11'):
          '''
          :type p1: integer
          :type p2: integer
          :type p3: float
          :type p4: float
          :type p5: int
          :type p6: integer
          :type p7: integer
          :type p8: int
          :type p9: int
          :type p10: int
          :type p11: string
          '''
          return p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10 + int(p11)

      def test():
          p7 = int('7')
          f1(1, '2', 3.0, 4, 5, int('6'), p7, p8=-8, p9='foo', p10='foo')
      #         │                                    │         ^^^^^^^^^ WARNING Expected type 'int', got 'Literal["foo"]' instead
      #         │                                    ^^^^^^^^ WARNING Expected type 'int', got 'Literal["foo"]' instead
      #         ^^^ WARNING Expected type 'int', got 'Literal["2"]' instead
      """.trimIndent())
  }

  @Nested
  inner class DocstringDerivedTypesNumpyGoogle {
    @Test
    @TestFor(issues = ["PY-24923"])
    fun `empty numpy function docstring`() = test("""
      def f(param):
          ''''''
          expr = param
      #   └ TYPE Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24923"])
    fun `empty numpy class docstring`() = test("""
      class C:
          ''''''
          def __init__(self, param):
              expr = param
      #       └ TYPE Unknown
      """.trimIndent())

    @Test
    fun `no type in google docstring param annotation`() = test("""
      def f(x: int):
          '''
          Args:
              x: foo
          '''    
          expr = x
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-16987"])
    fun `unfilled type in google docstring param annotation`() = test("""
      def f(x: int):
          '''
          Args:
              x (): foo
          '''    
          expr = x
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-16987"])
    fun `no type in numpy docstring param annotation`() = test("""
      def f(x: int):
          '''
          Parameters
          ----------
          x
              foo
          '''
          expr = x
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-17010"])
    fun `annotated return type precedes docstring`() = test("""
      def func() -> int:
          '''
          Returns:
              str
          '''
      expr = func()
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-17010"])
    fun `annotated param type precedes docstring`() = test("""
      def func(x: int):
          '''
          Args:
              x (str):
          '''
          expr = x
      #   └ TYPE int
      """.trimIndent())

    @Test
    fun `async function return type in docstring`() = test("""
      async def f():
          '''
          :rtype: int
          '''
          pass
      expr = f()
      #└ TYPE CoroutineType[Unknown, Unknown, int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27518"])
    fun `async function return type in numpy docstring`() = test("""
      async def f():
          '''
          An integer.
      
          Returns
          -------
          int
              A number
          '''
          pass
      expr = f()
      #└ TYPE CoroutineType[Unknown, Unknown, int]
      """.trimIndent())
  }

  @Nested
  inner class TypeComments {
    @Test
    fun `quoted forward reference in type comment`() = test("""
      def foo(x):
          # type: (MyClass) -> None
          expr = x
      #   └ TYPE MyClass
      
      class MyClass: ...
      """.trimIndent())
  }

  @Nested
  inner class DunderDocDunderClass {
    @Test
    @TestFor(issues = ["PY-35885"])
    fun `function dunder doc`() = test("""
      def example():
          '''Example Docstring'''
          return 0
      expr = example.__doc__
      #└ TYPE str
      """.trimIndent())

    // For a class object, __class__ points to its metaclass, not an attribute defined on C itself.
    // Here C is an instance of type[C], so C.__class__ has type type[C].
    @Test
    fun `dunder class on class object`() = test("""
      class C:
          pass
      expr = C.__class__
      #└ TYPE type[C]
      """.trimIndent())
  }

  @Nested
  inner class BinaryUnaryExpressionResultTypes {
    @Test
    fun `binary expr int`() = test("""
      expr = 1 + 2
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `binary expr str`() = test("""
      expr = '1' + '2'
      #└ TYPE LiteralString
      """.trimIndent())

    @Test
    fun `binary expr str format`() = test("""
      expr = '%s' % ('a')
      #└ TYPE LiteralString
      """.trimIndent())

    @Test
    fun `binary expr list`() = test("""
      expr = [1] + [2]
      #└ TYPE list[int]
      """.trimIndent())

    @Test
    fun `unary expr type`() = test("""
      expr = -1
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `logical and expression`() = test("""
      expr = 'foo' and 2
      #└ TYPE Literal["foo", 2]
      """.trimIndent())

    @Test
    fun `logical not expression`() = test("""
      expr = not 'hello'
      #└ TYPE bool
      """.trimIndent())

    @Test
    fun `bitwise or operator overload`() = test("""
      class A:
        def __or__(self, other) -> int: return 5
      
      expr = A() | A()
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `bitwise or operator overload returning union of metaclass`() = test("""
      class MyMeta(type):
          def __or__(self, other):
              return other
      
      class Foo(metaclass=MyMeta):
          ...
      
      expr = Foo | None
      #└ TYPE UnionType | Self
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71748"])
    fun `dunder eq applied from left to right by default`() = test("""
      from typing import Any
      
      class A:
        def __eq__(self, other: Any) -> int: ...
      #                                 ^^^ WARNING Return type of method 'A.__eq__()' does not match return type the base method in class 'object'
      
      class B:
        def __eq__(self, other: Any) -> str: ...
      #                                 ^^^ WARNING Return type of method 'B.__eq__()' does not match return type the base method in class 'object'
      
      a = A()
      b = B()
      expr = a == b
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71748"])
    fun `dunder ne applied from left to right by default`() = test("""
      from typing import Any
      
      class A:
        def __ne__(self, other: Any) -> int: ...
      #                                 ^^^ WARNING Return type of method 'A.__ne__()' does not match return type the base method in class 'object'
      
      class B:
        def __ne__(self, other: Any) -> str: ...
      #                                 ^^^ WARNING Return type of method 'B.__ne__()' does not match return type the base method in class 'object'
      
      a = A()
      b = B()
      expr = a != b
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression left precedence over right`() = test("""
      class A:
          def __add__(self, other: B) -> str: ...
      
      class B:
          def __radd__(self, other: A) -> int: ...
      
      expr = A() + B()
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression fallback to radd`() = test("""
      class E:
          pass
      
      class F:
          def __radd__(self, other: E) -> bool: ...
      
      expr = E() + F()
      #└ TYPE bool
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression rtruediv`() = test("""
      class D1:
          pass
      
      class D2:
          def __rtruediv__(self, other: D1) -> str: ...
      
      expr = D1() / D2()
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression union left all have add`() = test("""
      class A:
          def __add__(self, other: int) -> float: ...
      
      class B:
          def __add__(self, other: int) -> str: ...
      
      x: A | B = A()
      expr = x + 1
      #└ TYPE float | int | str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression different return types`() = test("""
      class A:
          def __sub__(self, other: int) -> str: ...
      
      expr = A() - 1
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression mul precedence`() = test("""
      class A:
          def __mul__(self, other: B) -> str: ...
      
      class B:
          def __rmul__(self, other: A) -> int: ...
      
      expr = A() * B()
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression does not prefer reflected if it does not match arguments`() = test("""
      class A:
          def __add__(self, other: B) -> str: ...
      
      class B(A):
          def __radd__(self, other: int) -> int: ...
      
      expr = A() + B()
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression does not prefer reflected for unrelated types`() = test("""
      class A:
          def __mul__(self, other: B) -> str: ...
      
      class B:
          def __rmul__(self, other: A) -> int: ...
      
      expr = A() * B()
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression inherited left operator matches`() = test("""
      class Right:
          def __radd__(self, other: 'Super') -> str: ...
      
      class Super:
          def __add__(self, other: Right) -> int: ...
      
      class Sub(Super):
          pass
      
      expr = Sub() + Right()
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression when right operand is subtype of left`() = test("""
      class A:
          def __add__(self, other: B) -> str: ...
      
      class B(A):
          def __radd__(self, other: A) -> int: ...
      
      expr = A() + B()
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression when right operand is inherited subtype of left`() = test("""
      class A:
          def __add__(self, other: BBase) -> str: ...
      
      class BBase(A):
          def __radd__(self, other: A) -> int: ...
      
      class B(BBase):
          pass
      
      expr = A() + B()
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression when right operand is union subtype of left`() = test("""
      class A:
          def __add__(self, other: BBase) -> str: ...
      
      class BBase(A):
          def __radd__(self, other: A) -> int: ...
      
      class B1(BBase):
          pass
      
      class B2(BBase):
          pass
      
      x: B1 | B2 = B1()
      expr = A() + x
      #└ TYPE str
      """.trimIndent())

    @Test
    fun `builtin numeric`() = test("""
      def test():
          abs(False)
          int(10)
          long(False)
      #   ^^^^ ERROR Unresolved reference 'long'
          float(False)
          complex(False)
          divmod(False, False)
          divmod('foo', u'bar')
      #         ^^^^^^^^^^^^^^^ WARNING No overload of 'divmod' matches the arguments. Argument types: (Literal["foo"], Literal["bar"]). Expected one of: (x: SupportsDivMod[_T_contra, _T_co], y: str), (x: str, y: SupportsRDivMod[str, _T_co])
          pow(False, True)
          round(False, 'foo')
      #        ^^^^^^^^^^^^^^ WARNING No overload of 'round' matches the arguments. Argument types: (Literal[False], Literal["foo"]). Expected one of: (number: _SupportsRound1[int], ndigits: None), (number: _SupportsRound2[int], ndigits: SupportsIndex)
      """.trimIndent())

    @Test
    fun `comparison operators`() = test("""
      def test():
          def f(x):
              '''
              :type x: str
              '''
              pass
          class C(object):
              def __gt__(self, other):
                  return []
          o = object()
          c = C()
          f(1 < 2)
      #     ^^^^^ WARNING Expected type 'str', got 'bool' instead
          f(o == o)
      #     ^^^^^^ WARNING Expected type 'str', got 'bool' instead
          f(o >= o)
          f('foo' > 'bar')
      #     ^^^^^^^^^^^^^ WARNING Expected type 'str', got 'bool' instead
          f(c < 1)
      #     └ WARNING Expected type 'int', got 'C' instead
      #     ^^^^^ WARNING Expected type 'str', got 'bool' instead
          f(c > 1)
      #     ^^^^^ WARNING Expected type 'str', got 'list[Unknown]' instead
          f(c == 1)
      #     ^^^^^^ WARNING Expected type 'str', got 'bool' instead
          f(c in [1, 2, 3])
      #     ^^^^^^^^^^^^^^ WARNING Expected type 'str', got 'bool' instead
      """.trimIndent())

    @Test
    fun `right operators`() = test("""
      class C(object):
          pass

      def test_right_operators():
          o = C()
          xs = [ o * [], ]
      #          └ WARNING Expected type 'SupportsIndex', got 'C' instead
      """.trimIndent())

    @Test
    fun `string integer`() = test("""
      def test():
          print('foo' + 'bar')
          print(2 + 3)
          print('foo' + 3)
      #                 └ WARNING No overload of '__add__' matches the arguments. Argument types: (Literal[3]). Expected one of: (value: LiteralString), (value: str)
          print(3 + 'foo')
      #             ^^^^^ WARNING Expected type 'int', got 'Literal["foo"]' instead
          print(3 + 3.14)
          print('foo' + 'bar' * 3)
          print('foo' + 3 * 'bar')
          print('foo' + 2 * 3)
      #                 ^^^^^ WARNING No overload of '__add__' matches the arguments. Argument types: (int). Expected one of: (value: LiteralString), (value: str)
      """.trimIndent())

    @Test
    fun `comparison operators for numeric types`() = test("""
      def f(x):
          print(x < 0, x <= 0, x > 0, x >= 0, x != 0)
          print(x.foo)

      print(f(True))
      #       ^^^^ WARNING Type 'Literal[True]' doesn't have expected attribute 'foo'
      print(f(0))
      #       └ WARNING Type 'Literal[0]' doesn't have expected attribute 'foo'
      print(f(3.14))
      #       ^^^^ WARNING Type 'float' doesn't have expected attribute 'foo'
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-23367"])
    fun `comparing float and int`() = test("""
      result = 0 > 0.0
      result2 = 0 < 0.0

      result3 = 0.0 > 0
      result4 = 0.0 < 0
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-9662"])
    fun `binary expression with unknown operand`() = test("""
      from typing import Any

      def f(x: Any) -> str:
          return x * 2

      def f(x: Any) -> str:
          return 2 * x

      def f(x) -> str:
          return x * 2

      def f(x) -> str:
          return 2 * x
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-32205"])
    fun `right shift operator accepts matching argument`() = test("""
      class Bin:
          def __rshift__(self, other: int):
              pass

      Bin() >> 1
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-13394"])
    fun `contains arguments`() = test("""
      class C(object):
          def __contains__(self, item):
              '''
              :type item: int
              '''
              return False

      def test():
          c = C()
          i = 10
          s = 'string'
          c in i
          i in c
          s in c
      #   └ WARNING Expected type 'int', got 'Literal["string"]' instead
      """.trimIndent())
  }

  @Nested
  inner class AugmentedAssignmentResultTypes {
    @Test
    fun `augmented assignment iadd same type`() = test("""
      class MutableContainer:
          def __iadd__(self, other: int) -> MutableContainer:
              return self

      m = MutableContainer()
      m += 1
      expr = m
      #└ TYPE MutableContainer
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment iadd self`() = test("""
      from typing import Self

      class MutableContainer:
          def __iadd__(self, other: int) -> Self:
              return self

      m = MutableContainer()
      m += 1
      expr = m
      #└ TYPE MutableContainer
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment iadd different type`() = test("""
      class IAddReturnsDifferent:
          def __iadd__(self, other: int) -> str:
              return "result"

      d = IAddReturnsDifferent()
      d += 1
      expr = d
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment fallback to add`() = test("""
      class AddOnly:
          def __add__(self, other: int) -> int:
              return 1

      a = AddOnly()
      a += 4
      expr = a
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment fallback to radd`() = test("""
      class NoOps:
          pass

      class RightOperand:
          def __radd__(self, other: NoOps) -> float:
              return 1.0

      n = NoOps()
      n += RightOperand()
      expr = n
      #└ TYPE float | int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment iadd signature mismatch fallback to add`() = test("""
      class IAddAnnotatedAdd:
          def __iadd__(self, other: str) -> IAddAnnotatedAdd:
              return self
          def __add__(self, other: int) -> float:
              return 1.0

      ia = IAddAnnotatedAdd()
      ia += 5
      expr = ia
      #└ TYPE float | int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment builtin int`() = test("""
      x: int = 1
      x += 1
      expr = x
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment builtin int widens to float`() = test("""
      y: int = 1
      y += 1.5
      #^^^^^^^ WARNING Expected type 'int' for augmented assignment, got 'float | int' from operation instead
      expr = y
      #└ TYPE float | int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment builtin list`() = test("""
      lst: list[int] = [1, 2]
      lst += [3, 4]
      expr = lst
      #└ TYPE list[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment builtin str`() = test("""
      s: str = "hello"
      s += " world"
      expr = s
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment generic iadd`() = test("""
      class MyList[T]:
          def __iadd__(self, other: list[T]) -> MyList[T]:
              return self

      ml = MyList[int]()
      ml += [1, 2, 3]
      expr = ml
      #└ TYPE MyList[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment in loop`() = test("""
      class Accumulator:
          def __iadd__(self, other: int) -> Accumulator:
              return self

      acc = Accumulator()
      for i in range(10):
          acc += i
      expr = acc
      #└ TYPE Accumulator
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    @TestCaseOptions(enableWeakWarnings = false)
    fun `augmented assignment type changes in loop`() = test("""
      class Counter:
          def __add__(self, other: int) -> int:
              return 0

      c = Counter()
      while True:
          c += 1
          if bool():
              break
      expr = c
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment sub operator`() = test("""
      class SubOnly:
          def __sub__(self, other: int) -> str:
              return ""

      sub = SubOnly()
      sub -= 1
      expr = sub
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment mul operator`() = test("""
      class MulOnly:
          def __mul__(self, other: int) -> float:
              return 0.0

      mul = MulOnly()
      mul *= 3
      expr = mul
      #└ TYPE float | int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment truediv operator`() = test("""
      class DivOnly:
          def __truediv__(self, other: int) -> complex:
              return 0j

      div = DivOnly()
      div /= 2
      expr = div
      #└ TYPE complex | float | int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment subclass iadd`() = test("""
      class Base:
          def __iadd__(self, other: int) -> Base:
              return self

      class Child(Base):
          def __iadd__(self, other: int) -> Child:
              return self

      b: Base = Child()
      b += 1
      expr = b
      #└ TYPE Base
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment None return`() = test("""
      class BadIAdd:
          def __iadd__(self, other: int) -> None:
              pass

      bad = BadIAdd()
      bad += 1
      expr = bad
      #└ TYPE None
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment iadd precedence over add`() = test("""
      class Multi:
          def __iadd__(self, other: int) -> str:
              return ""
          def __add__(self, other: int) -> float:
              return 0.0

      p = Multi()
      p += 1
      expr = p
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    @TestCaseOptions(enableWeakWarnings = false)
    fun `augmented assignment union iadd and add`() = test("""
      class P:
          def __iadd__(self, other: int) -> P: ...

      class Q:
          def __add__(self, other: int) -> str: ...

      u: P | Q = P()
      u += 1
      #^^^^^ WARNING Expected type 'P | Q' for augmented assignment, got 'P | str' from operation instead
      expr = u
      #└ TYPE P | str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment union all iadd`() = test("""
      class A:
          def __iadd__(self, other: int) -> str: ...
      
      class B:
          def __iadd__(self, other: int) -> bool: ...
      
      x: A | B = A()
      x += 1
      #^^^^^ WARNING Expected type 'A | B' for augmented assignment, got 'str | bool' from operation instead
      expr = x
      #└ TYPE str | bool
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    @TestCaseOptions(enableWeakWarnings = false)
    fun `augmented assignment union sub operator`() = test("""
      class A:
          def __isub__(self, other: int) -> int: ...

      class B:
          def __sub__(self, other: int) -> str: ...

      x: A | B = A()
      x -= 1
      #^^^^^ WARNING Expected type 'A | B' for augmented assignment, got 'int | str' from operation instead
      expr = x
      #└ TYPE int | str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment union inplace precedence per class`() = test("""
      class A:
          def __iadd__(self, other: int) -> str: ...
          def __add__(self, other: int) -> float: ...
      
      class B:
          def __iadd__(self, other: int) -> bool: ...
          def __add__(self, other: int) -> complex: ...
      
      x: A | B = A()
      x += 1
      #^^^^^ WARNING Expected type 'A | B' for augmented assignment, got 'str | bool' from operation instead
      expr = x
      #└ TYPE str | bool
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment inherited left operator matches`() = test("""
      from typing import Any
      
      class Super:
          def __iadd__(self, other: Any) -> str: ...
      
      class Sub(Super):
          pass
      
      class Operand:
          def __radd__(self, other: Super) -> int: ...
      
      x = Sub()
      x += Operand()
      expr = x
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment radd defined but iadd missing on target`() = test("""
      class A: pass
      class B:
          def __radd__(self, other: A) -> str: ...

      a = A()
      a += B()

      b = B()
      b += A() # WARNING Class 'B' does not define '__iadd__', so the '+=' operator cannot be used on its instances
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment iadd not defined on class`() = test("""
      class A: pass

      a = A()
      a += a # WARNING Class 'A' does not define '__iadd__', so the '+=' operator cannot be used on its instances
      """)

    @Test
    @TestFor(issues = ["PY-6925"])
    fun `assigned operator`() = test("""
      def f(x):
          return x

      class C(object):
          __div__, __rdiv__ = f(0)

      c = C()
      print(c / 2)
      #     └ WARNING Expected type 'int', got 'C' instead
      """.trimIndent())
  }

  @Test
  @TestFor(issues = ["PY-7757"])
  fun `result of text open read is str`() = test("""
    def f(s: str):
        pass

    def g(s: int):
        pass

    f(open('foo').read())
    g(open('foo').read()) # WARNING Expected type 'int', got 'str' instead
    """.trimIndent())
}

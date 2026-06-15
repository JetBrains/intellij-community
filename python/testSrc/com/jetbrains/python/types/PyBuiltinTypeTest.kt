// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

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
class PyBuiltinTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class OpenAndIoOpen {
    @Test
    fun `open default mode`() = test("""
      expr = open('foo')
      # └ TYPE TextIOWrapper[_WrappedBuffer]
      """)

    @Test
    fun `open text mode`() = test("""
      expr = open('foo', 'r')
      # └ TYPE TextIOWrapper[_WrappedBuffer]
      """)

    @Test
    fun `open binary mode`() = test("""
      expr = open('foo', 'rb')
      #└ TYPE BufferedReader[_BufferedReaderStream]
      """)

    @Test
    fun `io open default mode`() = test(
      TestOptions(enablePyAnyType = false),
      """
      import io
      expr = io.open('foo')
      # └ TYPE TextIOWrapper[_WrappedBuffer]
      """,
    )

    @Test
    fun `io open text mode`() = test(
      TestOptions(enablePyAnyType = false),
      """
      import io
      expr = io.open('foo', 'r')
      #└ TYPE TextIOWrapper[_WrappedBuffer]
      """,
    )

    @Test
    fun `io open binary mode`() = test(
      TestOptions(enablePyAnyType = false),
      """
      import io
      expr = io.open('foo', 'rb')
      #└ TYPE BufferedReader[_BufferedReaderStream]
      """,
    )
  }

  @Nested
  inner class Input {
    @Test
    @TestFor(issues = ["PY-21350"])
    fun `input result`() = test("""
      expr = input()
      #└ TYPE str
      """)
  }

  @Nested
  inner class MinMaxSum {
    @Test
    fun `min result`() = test("""
      expr = min(1, 2, 3)
      #└ TYPE int
      """)

    @Test
    fun `max result`() = test("""
      expr = max(1, 2, 3)
      # └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-21692"])
    fun `sum result`() = test("""
      expr = sum([1, 2, 3])
      #└ TYPE int
      """)

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
      """)
  }

  @Nested
  inner class Round {
    @Test
    @TestFor(issues = ["PY-13750"])
    fun `round of int without ndigits`() = test("""
      expr = round(1)
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `round of int with ndigits`() = test("""
      expr = round(1, 1)
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `round of float without ndigits`() = test("""
      expr = round(1.1)
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `round of float with ndigits`() = test("""
      expr = round(1.1, 1)
      #└ TYPE float | int
      """)

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `round of bool without ndigits`() = test("""
      expr = round(True)
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `round of bool with ndigits`() = test("""
      expr = round(True, 1)
      #└ TYPE int
      """)
  }

  @Nested
  inner class FloatFromhex {
    @Test
    @TestFor(issues = ["PY-21083"])
    fun `float fromhex`() = test(
      TestOptions(enablePyAnyType = false),
      """
      expr = float.fromhex("0.5")
      #└ TYPE float
      """,
    )
  }

  @Nested
  inner class LiteralsBytesFStringListOfClass {
    @Test
    @TestFor(issues = ["PY-1427"])
    fun `bytes literal`() = test("""
      expr = b'foo'
      #└ TYPE bytes
      """)

    @Test
    @TestFor(issues = ["PY-29665"])
    fun `raw bytes literal rb`() = test("""
      expr = rb'raw bytes'
      #└ TYPE bytes
      """)

    @Test
    @TestFor(issues = ["PY-29665"])
    fun `raw bytes literal br`() = test("""
      expr = br'raw bytes'
      #└ TYPE bytes
      """)

    @Test
    fun `f-string literal type`() = test("""
      expr = f'foo'
      #└ TYPE str
      """)

    @Test
    fun `list literal of class object`() = test("""
      expr = [float]
      #└ TYPE list[type[float]]
      """)
  }

  @Nested
  inner class CollectionsStdlibTypes {
    @Test
    fun `defaultdict from dict`() = test(
      TestOptions(enablePyAnyType = false),
      """
      from collections import defaultdict
      expr = defaultdict(dict)
      #└ TYPE defaultdict[Any, dict[Any, Any]]
      """,
    )
  }

  @Nested
  inner class DictBuiltinMethodResults {
    @Test
    @TestFor(issues = ["PY-20409"])
    fun `get from dict with default None value`() = test(
      TestOptions(enablePyAnyType = false),
      """
      d = {}
      expr = d.get("abc", None)
      #└ TYPE Any | None
      """,
    )

    @Test
    @TestFor(issues = ["PY-82818"])
    fun `pop from dict with default None value`() = test(
      TestOptions(enablePyAnyType = false),
      """
      d = {}
      expr = d.pop("abc", None)
      #└ TYPE Any
      """,
    )

    @Test
    @TestFor(issues = ["PY-83704"])
    fun `pop from typed dict with default None value`() = test(
      TestOptions(enablePyAnyType = false),
      """
      d: dict[str, int] = {"abc": 0, "1": 1}
      expr = d.pop("abc", None)
      #└ TYPE int | None
      """,
    )

    @Test
    @TestFor(issues = ["PY-83704"])
    fun `pop from Any-valued dict with default None value`() = test(
      TestOptions(enablePyAnyType = false),
      """
      from typing import Any
      d: dict[str, Any] = {"abc": "s", "1": 1}
      expr = d.pop("abc", None)
      #└ TYPE Any
      """,
    )
  }

  @Nested
  inner class DocstringDerivedTypesReStructuredText {
    @Test
    fun `type from method call comment`() = test(
      TestOptions(enablePyAnyType = false),
      """
      expr = ''.capitalize()
      #└ TYPE LiteralString
      """,
    )

    @Test
    fun `rest param type`() = test(
      TestOptions(enablePyAnyType = false),
      """
      def foo(limit):
        ''':param integer limit: maximum number of stack frames to show'''
        expr = limit
      #   └ TYPE int
      """,
    )

    @Test
    @TestFor(issues = ["PY-3849"])
    fun `rest class type`() = test(
      TestOptions(enablePyAnyType = false),
      """
      class Foo: pass
      def foo(limit):
        ''':param :class:`Foo` limit: maximum number of stack frames to show'''
        expr = limit
      #   └ TYPE Foo
      """,
    )

    @Test
    fun `rest ivar type`() = test(
      TestOptions(enablePyAnyType = false),
      """
      def foo(p):
          var = p.bar
          ''':type var: str'''
          expr = var
      #   └ TYPE str
      """,
    )

    @Test
    @TestFor(issues = ["PY-6584"])
    fun `class attribute type in class docstring via class`() = test(
      TestOptions(enablePyAnyType = false),
      """
      class C(object):
          '''
          :type foo: int
          '''
          foo = None
      
      expr = C.foo
      #└ TYPE int
      """,
    )

    @Test
    @TestFor(issues = ["PY-6584"])
    fun `class attribute type in class docstring via instance`() = test(
      TestOptions(enablePyAnyType = false),
      """
      class C(object):
          '''
          :type foo: int
          '''
          foo = None
      
      expr = C().foo
      #└ TYPE int
      """,
    )

    @Test
    @TestFor(issues = ["PY-6584"])
    fun `instance attribute type in class docstring`() = test(
      TestOptions(enablePyAnyType = false),
      """
      class C(object):
          '''
          :type foo: int
          '''
          def __init__(self, bar):
              self.foo = bar
      
      def f(x):
          expr = C(x).foo
      #   └ TYPE int
      """,
    )

    @Test
    @TestFor(issues = ["PY-8953"])
    fun `self type in docstring`() = test(
      TestOptions(enablePyAnyType = false),
      """
      class C(object):
          def foo(self):
              '''
              :type self: int
              '''
              expr = self
      #       └ TYPE int
      """,
    )

    @Test
    @TestFor(issues = ["PY-7322"])
    fun `namedtuple parameter type in docstring`() = test(
      TestOptions(enablePyAnyType = false),
      """
      from collections import namedtuple
      Point = namedtuple('Point', ('x', 'y'))
      def takes_a_point(point):
          '''
          :type point: Point
          '''
          expr = point
      #   └ TYPE Point
      """,
    )

    @Test
    @TestFor(issues = ["PY-4813"])
    fun `parameter type inference in subclass from docstring`() = test(
      TestOptions(enablePyAnyType = false),
      """
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
      """,
    )
  }

  @Nested
  inner class DocstringDerivedTypesNumpyGoogle {
    @Test
    @TestFor(issues = ["PY-24923"])
    fun `empty numpy function docstring`() = test(
      TestOptions(enablePyAnyType = false),
      """
      def f(param):
          ''''''
          expr = param
      #   └ TYPE Any
      """,
    )

    @Test
    @TestFor(issues = ["PY-24923"])
    fun `empty numpy class docstring`() = test(
      TestOptions(enablePyAnyType = false),
      """
      class C:
          ''''''
          def __init__(self, param):
              expr = param
      #       └ TYPE Any
      """,
    )

    @Test
    fun `no type in google docstring param annotation`() = test("""
      def f(x: int):
          '''
          Args:
              x: foo
          '''    
          expr = x
      #   └ TYPE int
      """)

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
      """)

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
      """)

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
      """)

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
      """)

    @Test
    fun `async function return type in docstring`() = test(
      TestOptions(enablePyAnyType = false),
      """
      async def f():
          '''
          :rtype: int
          '''
          pass
      expr = f()
      #└ TYPE CoroutineType[Any, Any, int]
      """,
    )

    @Test
    @TestFor(issues = ["PY-27518"])
    fun `async function return type in numpy docstring`() = test(
      TestOptions(enablePyAnyType = false),
      """
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
      #└ TYPE CoroutineType[Any, Any, int]
      """,
    )
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
      """)
  }

  @Nested
  inner class DunderDocDunderClass {
    @Test
    @TestFor(issues = ["PY-35885"])
    fun `function dunder doc`() = test(
      TestOptions(enablePyAnyType = false),
      """
      def example():
          '''Example Docstring'''
          return 0
      expr = example.__doc__
      #└ TYPE str
      """,
    )

    @Test
    fun `dunder class on class object`() = test("""
      class C:
          pass
      expr = C.__class__
      #└ TYPE property
      """)
  }

  @Nested
  inner class BinaryUnaryExpressionResultTypes {
    @Test
    fun `binary expr int`() = test("""
      expr = 1 + 2
      #└ TYPE int
      """)

    @Test
    fun `binary expr str`() = test("""
      expr = '1' + '2'
      #└ TYPE LiteralString
      """)

    @Test
    fun `binary expr str format`() = test("""
      expr = '%s' % ('a')
      #└ TYPE LiteralString
      """)

    @Test
    fun `binary expr list`() = test("""
      expr = [1] + [2]
      #└ TYPE list[int]
      """)

    @Test
    fun `unary expr type`() = test("""
      expr = -1
      #└ TYPE int
      """)

    @Test
    fun `logical and expression`() = test("""
      expr = 'foo' and 2
      #└ TYPE str | int
      """)

    @Test
    fun `logical not expression`() = test("""
      expr = not 'hello'
      #└ TYPE bool
      """)

    @Test
    fun `bitwise or operator overload`() = test("""
      class A:
        def __or__(self, other) -> int: return 5
      
      expr = A() | A()
      #└ TYPE int
      """)

    @Test
    fun `bitwise or operator overload returning union of metaclass`() = test("""
      class MyMeta(type):
          def __or__(self, other):
              return other
      
      class Foo(metaclass=MyMeta):
          ...
      
      expr = Foo | None
      #└ TYPE UnionType | Self
      """)

    @Test
    @TestFor(issues = ["PY-71748"])
    fun `dunder eq applied from left to right by default`() = test("""
      from typing import Any
      
      class A:
        def __eq__(self, other: Any) -> int: ...
      
      class B:
        def __eq__(self, other: Any) -> str: ...
      
      a = A()
      b = B()
      expr = a == b
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-71748"])
    fun `dunder ne applied from left to right by default`() = test("""
      from typing import Any
      
      class A:
        def __ne__(self, other: Any) -> int: ...
      
      class B:
        def __ne__(self, other: Any) -> str: ...
      
      a = A()
      b = B()
      expr = a != b
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression left precedence over right`() = test("""
      class A:
          def __add__(self, other: B) -> str: ...
      
      class B:
          def __radd__(self, other: A) -> int: ...
      
      expr = A() + B()
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression fallback to radd`() = test("""
      class E:
          pass
      
      class F:
          def __radd__(self, other: E) -> bool: ...
      
      expr = E() + F()
      #└ TYPE bool
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression rtruediv`() = test("""
      class D1:
          pass
      
      class D2:
          def __rtruediv__(self, other: D1) -> str: ...
      
      expr = D1() / D2()
      #└ TYPE str
      """)

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
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression different return types`() = test("""
      class A:
          def __sub__(self, other: int) -> str: ...
      
      expr = A() - 1
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression mul precedence`() = test("""
      class A:
          def __mul__(self, other: B) -> str: ...
      
      class B:
          def __rmul__(self, other: A) -> int: ...
      
      expr = A() * B()
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression does not prefer reflected if it does not match arguments`() = test("""
      class A:
          def __add__(self, other: B) -> str: ...
      
      class B(A):
          def __radd__(self, other: int) -> int: ...
      
      expr = A() + B()
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression does not prefer reflected for unrelated types`() = test("""
      class A:
          def __mul__(self, other: B) -> str: ...
      
      class B:
          def __rmul__(self, other: A) -> int: ...
      
      expr = A() * B()
      #└ TYPE str
      """)

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
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `binary expression when right operand is subtype of left`() = test("""
      class A:
          def __add__(self, other: B) -> str: ...
      
      class B(A):
          def __radd__(self, other: A) -> int: ...
      
      expr = A() + B()
      #└ TYPE str
      """)

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
      """)

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
      """)
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
      """)

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
      """)

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
      """)

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
      """)

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
      """)

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
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment builtin int`() = test("""
      x: int = 1
      x += 1
      expr = x
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment builtin int widens to float`() = test("""
      y: int = 1
      y += 1.5
      #^^^^^^^ WARNING Expected type 'int' for augmented assignment, got 'float | int' from operation instead
      expr = y
      #└ TYPE float | int
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment builtin list`() = test("""
      lst: list[int] = [1, 2]
      lst += [3, 4]
      expr = lst
      #└ TYPE list[int]
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment builtin str`() = test("""
      s: str = "hello"
      s += " world"
      expr = s
      #└ TYPE str
      """)

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
      """)

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
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment type changes in loop`() = test(
      TestOptions(enableWeakWarnings = false),
      """
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
      """,
    )

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
      """)

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
      """)

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
      """)

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
      """)

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
      """)

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
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment union iadd and add`() = test(
      TestOptions(enableWeakWarnings = false),
      """
      class P:
          def __iadd__(self, other: int) -> P: ...

      class Q:
          def __add__(self, other: int) -> str: ...

      u: P | Q = P()
      u += 1
      #^^^^^ WARNING Expected type 'P | Q' for augmented assignment, got 'P | str' from operation instead
      expr = u
      #└ TYPE P | str
      """,
    )

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
      """)

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment union sub operator`() = test(
      TestOptions(enableWeakWarnings = false),
      """
      class A:
          def __isub__(self, other: int) -> int: ...

      class B:
          def __sub__(self, other: int) -> str: ...

      x: A | B = A()
      x -= 1
      #^^^^^ WARNING Expected type 'A | B' for augmented assignment, got 'int | str' from operation instead
      expr = x
      #└ TYPE int | str
      """,
    )

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
      """)

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
      """)
  }

  @Test
  @TestFor(issues = ["PY-32205"])
  fun `right shift operator accepts matching argument`() = test(TestOptions(enablePyAnyType = false), """
    class Bin:
        def __rshift__(self, other: int):
            pass

    Bin() >> 1
    """)

  @Test
  @TestFor(issues = ["PY-7757"])
  fun `result of text open read is str`() = test(TestOptions(enablePyAnyType = false), """
    def f(s: str):
        pass

    def g(s: int):
        pass

    f(open('foo').read())
    g(open('foo').read()) # WARNING Expected type 'int', got 'str' instead
    """)
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for narrowing: `isinstance`/`issubclass` narrowing, `is None`/`is not None`,
 * `assert`, identity/equality narrowing, `TypeGuard`, `TypeIs`, conditional/else narrowing, narrowing in
 * conditional expressions and comprehensions, and structural-type `isinstance` checks.
 */
class PyNarrowingTypeTest : PyCodeInsightTestCase() {

  override val defaultTestOptions = TestOptions(enablePyAnyType = false)

  @Nested
  inner class IsinstanceNarrowing {
    @Test
    fun `isinstance narrows in if`() = test("""
      def f(c):
          def g():
              '''
              :rtype: int or str
              '''
          x = g()
          if isinstance(x, str):
              expr = x
      #       └ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-2140"])
    fun `not isinstance narrows away tuple of types`() = test("""
      def f(c):
          def g():
              '''
              :rtype: int or str or list
              '''
          x = g()
          if not isinstance(x, (str, int)):
              expr = x
      #       └ TYPE list
      """)

    @Test
    fun `isinstance with non-type literals in tuple`() = test("""
      x = ""
      if isinstance(x, (1, "")):
          expr = x
      #   └ TYPE Literal[""]
      """)

    @Test
    fun `isinstance or of two checks`() = test("""
      def foo(a):
          if isinstance(a, int) or isinstance(a, str):
              expr = a
      #       └ TYPE int | str
      """)

    @Test
    fun `isinstance or of four checks`() = test("""
      class A:
          pass

      class B:
          pass

      def f(a: object):
          if isinstance(a, str) or isinstance(a, int) or isinstance(a, A) or isinstance(a, B):
              expr = a
      #       └ TYPE str | int | A | B
          else:
              pass
      """)

    @Test
    fun `isinstance and of two tuple checks`() = test("""
      class A:
          pass

      def f(a):
          if isinstance(a, (str, A)) and isinstance(a, (A, int)):
              expr = a
      #       └ TYPE A
      """)

    @Test
    fun `isinstance and of three tuple checks`() = test("""
      class A:
          pass

      class B:
          pass

      def f(a):
          if isinstance(a, (str, A)) and isinstance(a, (A, int)) and isinstance(a, (B, A)):
              expr = a
      #       └ TYPE A
      """)

    @Test
    fun `isinstance logical expressions`() = test("""
      class A:
          pass

      class B:
          pass

      def f(a):
          if isinstance(a, (str, A, int)) and not isinstance(a, (A, int)) or isinstance(a, B):
              expr = a
      #       └ TYPE str | B
      """)

    @Test
    @TestFor(issues = ["PY-4383"])
    fun `assertIsInstance narrows`() = test("""
      from unittest import TestCase

      class Test1(TestCase):
          def test_1(self, c):
              x = 1 if c else 'foo'
              self.assertIsInstance(x, int)
              expr = x
      #       └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-20679"])
    fun `isinstance is True`() = test("""
      a = None
      if isinstance(a, str) is True:
          expr = a
      #   └ TYPE Never
      raise TypeError('Invalid type')
      """)

    @Test
    @TestFor(issues = ["PY-20679"])
    fun `isinstance is True reversed`() = test("""
      a = None
      if True is isinstance(a, str):
          expr = a
      #   └ TYPE Never
      raise TypeError('Invalid type')
      """)

    @Test
    @TestFor(issues = ["PY-20679"])
    fun `isinstance is not False`() = test("""
      a = None
      if isinstance(a, str) is not False:
          expr = a
      #   └ TYPE Never
      raise TypeError('Invalid type')
      """)

    @Test
    @TestFor(issues = ["PY-20679"])
    fun `isinstance is not False reversed`() = test("""
      a = None
      if False is not isinstance(a, str):
          expr = a
      #   └ TYPE Never
      raise TypeError('Invalid type')
      """)

    @Test
    @TestFor(issues = ["PY-20679"])
    fun `not isinstance is False`() = test("""
      a = None
      if not isinstance(a, str) is False:
          expr = a
      #   └ TYPE Never
      raise TypeError('Invalid type')
      """)

    @Test
    @TestFor(issues = ["PY-20679"])
    fun `not False is isinstance`() = test("""
      a = None
      if not False is isinstance(a, str):
          expr = a
      #   └ TYPE Never
      raise TypeError('Invalid type')
      """)

    @Test
    @TestFor(issues = ["PY-20679"])
    fun `not isinstance is True narrows in else branch`() = test("""
      a = None
      if not isinstance(a, str) is True:
          raise TypeError('Invalid type')
      expr = a
      # └ TYPE Never
      """)

    @Test
    @TestFor(issues = ["PY-20679"])
    fun `not True is isinstance narrows in else branch`() = test("""
      a = None
      if not True is isinstance(a, str):
          raise TypeError('Invalid type')
      expr = a
      # └ TYPE Never
      """)

    @Test
    @TestFor(issues = ["PY-20679"])
    fun `isinstance is not True narrows in else branch`() = test("""
      a = None
      if isinstance(a, str) is not True:
          raise TypeError('Invalid type')
      expr = a
      #└ TYPE Never
      """)

    @Test
    @TestFor(issues = ["PY-20679"])
    fun `True is not isinstance narrows in else branch`() = test("""
      a = None
      if True is not isinstance(a, str):
          raise TypeError('Invalid type')
      expr = a
      #└ TYPE Never
      """)

    @Test
    @TestFor(issues = ["PY-20679"])
    fun `isinstance is False narrows in else branch`() = test("""
      a = None
      if isinstance(a, str) is False:
          raise TypeError('Invalid type')
      expr = a
      #└ TYPE Never
      """)

    @Test
    @TestFor(issues = ["PY-20679"])
    fun `False is isinstance narrows in else branch`() = test("""
      a = None
      if False is isinstance(a, str):
          raise TypeError('Invalid type')
      expr = a
      #└ TYPE Never
      """)

    @Test
    @TestFor(issues = ["PY-5084"])
    fun `isinstance else branch narrows`() = test("""
      def test(c):
          x = 'foo' if c else 42
          if isinstance(x, int):
              print(x)
          else:
              expr = x
      #       └ TYPE str
      """)

    @Test
    fun `isinstance expression resolved to tuple`() = test("""
      string_types = str, unicode # ERROR Unresolved reference 'unicode'

      def f(x):
          if isinstance(x, string_types):
              expr = x
      #       └ TYPE str
      """)

    @Test
    fun `isinstance in conditional expression`() = test("""
      def f(x):
          expr = x if isinstance(x, str) else 10
      #   └ TYPE str | int
      """)

    @Test
    @TestFor(issues = ["PY-11541"])
    fun `isinstance basestring check`() = test("""
      def f(x):
          if isinstance(x, basestring): # ERROR Unresolved reference 'basestring'
              expr = x
      #       └ TYPE Any
      """)

    @Test
    fun `structural type and isinstance checks`() = test("""
      def f(x):
          if isinstance(x, str):
              x.lower()
          x.foo

      expr = f
      #└ TYPE (x: {foo}) -> None
      """)

    @Test
    @TestFor(issues = ["PY-20818"])
    fun `isinstance for superclass via assert`() = test("""
      class A:
          pass
      class B(A):
          def foo(self):
              pass
      def test():
          b = B()
          assert(isinstance(b, A))
          expr = b
      #   └ TYPE B
      """)

    @Test
    fun `issubclass narrows`() = test("""
      class A: pass
      def foo(cls):
          if issubclass(cls, A):
              expr = cls
      #       └ TYPE type[A]
      """)

    @Test
    fun `issubclass with tuple of type objects`() = test("""
      class A: pass
      class B: pass
      def foo(cls):
          if issubclass(cls, (A, B)):
              expr = cls
      #       └ TYPE type[A | B]
      """)

    @Test
    @TestFor(issues = ["PY-26493"])
    fun `assert isinstance and structural type`() = test("""
      def run_workloads(cfg):
          assert isinstance(cfg, str)
          cfg.split()
          expr = cfg
      #   └ TYPE str
      """)

    @Test
    fun `after isinstance and attribute usage`() = test("""
      def bar(y):
          if isinstance(y, int):
              pass
          print(y.bar)
          expr = y
      #   └ TYPE {bar} | ({bar} & int)
      """)
  }

  @Nested
  inner class IsNoneNarrowing {
    @Test
    fun `is not None narrows`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if x is not None:
              expr = x
      #       └ TYPE int
      """)

    @Test
    fun `None is not x narrows`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if None is not x:
              expr = x
      #       └ TYPE int
      """)

    @Test
    fun `not x is None narrows`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if not x is None:
              expr = x
      #       └ TYPE int
      """)

    @Test
    fun `not None is x narrows`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if not None is x:
              expr = x
      #       └ TYPE int
      """)

    @Test
    fun `is None narrows to None`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if x is None:
              expr = x
      #       └ TYPE None
      """)

    @Test
    fun `None is x narrows to None`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if None is x:
              expr = x
      #       └ TYPE None
      """)

    @Test
    fun `Any is None narrows to None`() = test("""
      def test_1(c):
        if c is None:
          expr = c
      #   └ TYPE None
      """)

    @Test
    fun `else after is not None narrows to None`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if x is not None:
              print(x)
          else:
              expr = x
      #       └ TYPE None
      """)

    @Test
    fun `else after None is not x narrows to None`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if None is not x:
              print(x)
          else:
              expr = x
      #       └ TYPE None
      """)

    @Test
    fun `else after not x is None narrows to None`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if not x is None:
              print(x)
          else:
              expr = x
      #       └ TYPE None
      """)

    @Test
    fun `else after not None is x narrows to None`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if not None is x:
              print(x)
          else:
              expr = x
      #       └ TYPE None
      """)

    @Test
    fun `else after is None narrows away None`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if x is None:
              print(x)
          else:
              expr = x
      #       └ TYPE int
      """)

    @Test
    fun `else after None is x narrows away None`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if None is x:
              print(x)
          else:
              expr = x
      #       └ TYPE int
      """)

    @Test
    fun `else after Any is None stays Any`() = test("""
      def test_1(c):
        if c is None:
          print(c)
        else:
          expr = c
      #   └ TYPE Any
      """)

    @Test
    @TestFor(issues = ["PY-21897"])
    fun `else after if reference statement stays Any`() = test("""
      def test(a):
        if a:
          print(a)
        else:
          expr = a
      #   └ TYPE Any
      """)

    @Test
    @TestFor(issues = ["PY-21626"])
    fun `nested conflicting is None checks initial Any`() = test("""
      def f(x):
          if x is None:
              if x is not None:
                  pass
          expr = x
      #   └ TYPE Any | None
      """)

    @Test
    @TestFor(issues = ["PY-21626"])
    fun `nested conflicting is None checks initial known`() = test("""
      x = 'foo'
      if x is None:
          if x is not None:
              pass
      expr = x
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-21175"])
    fun `None filtered out by conditional assignment`() = test("""
      xs = None
      if xs is None:
          xs = [1, 2, 3]
      expr = xs
      #└ TYPE list[int]
      """)

    @Test
    @TestFor(issues = ["PY-29748"])
    fun `narrowing after identity comparison`() = test("""
      a = 1
      if a is a:
         expr = a
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-32113"])
    fun `assertion on variable from outer scope`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON35, enablePyAnyType = false, assertRecursionPrevention = false),
      """
      class B: pass

      class D(B): pass

      g_b: B = undefined
      #  │     ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #  ^^^ ERROR Python version 3.5 does not support variable annotations

      def main() -> None:
          assert isinstance(g_b, D)
          expr = g_b
      #   └ TYPE D
      """,
    )

    @Test
    @TestFor(issues = ["PY-32113"])
    fun `assertion on function from outer scope`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON35, enablePyAnyType = false, assertRecursionPrevention = false),
      """
      class B: pass

      def g_b():
          pass

      def main() -> None:
          assert isinstance(g_b, B)
          expr = g_b
      #   └ TYPE () -> None & B
      """,
    )
  }

  @Nested
  inner class IsinstanceIssubclassNarrowingPython3Latest {
    @Test
    @TestFor(issues = ["PY-83047"])
    fun `qualified reference type narrowing`() = test("""
      class C:
          def __init__(self):
              self.t: int | None = 5

          def f(self, x: float):
              if x < 0:
                  self.t = None

              expr = self.t
      #       └ TYPE int | None
      """)

    @Test
    @TestFor(issues = ["PY-83351"])
    fun `while statement narrowing`() = test("""
      def foo(x: int | None):
          while x:
              expr = x
      #       └ TYPE int
              x = None
      """)

    @Test
    @TestFor(issues = ["PY-83351"])
    fun `while statement narrowing through double negation`() = test("""
      def foo(x: int | None):
          while not (not (((not (not x))))):
              expr = x
      #       └ TYPE int
              x = None
      """)

    @Test
    @TestFor(issues = ["PY-83597"])
    fun `and expression narrowing`() = test("""
      def foo(x: int | None):
          x and (expr := x)
      #          └ TYPE int
      """)

    @Test
    fun `is not None narrows (py3)`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if x is not None:
              expr = x
      #       └ TYPE int
      """)

    @Test
    fun `is None narrows to None (py3)`() = test("""
      def test_1(self, c):
          x = 1 if c else None
          if x is None:
              expr = x
      #       └ TYPE None
      """)

    @Test
    fun `issubclass inside list comprehension`() = test("""
      class A: pass
      expr = [e for e in [] if issubclass(e, A)]
      #└ TYPE list[type[A]]
      """)

    @Test
    fun `isinstance inside list comprehension`() = test("""
      class A: pass
      expr = [e for e in [] if isinstance(e, A)]
      #└ TYPE list[A]
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `isinstance negative narrowing with variable does not narrow`() = test("""
      class A:
          pass

      def test(a: A | int, b: type[A]):
          if isinstance(a, b):
              return
          expr = a
      #   └ TYPE A | int
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `isinstance negative narrowing with class reference narrows`() = test("""
      class A:
          pass

      def test(a: A | int):
          if isinstance(a, A):
              return
          expr = a
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `isinstance negative narrowing with tuple of classes narrows`() = test("""
      class A:
          pass

      class B:
          pass

      def test(a: A | B | int):
          if isinstance(a, (A, B)):
              return
          expr = a
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `isinstance negative narrowing with tuple containing variable does not narrow`() = test("""
      class A:
          pass

      class B:
          pass

      def test(a: A | B | int, b: type[B]):
          if isinstance(a, (A, b)):
              return
          expr = a
      #   └ TYPE A | B | int
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `isinstance negative narrowing with union operator narrows`() = test("""
      class A:
          pass

      class B:
          pass

      def test(a: A | B | int):
          if isinstance(a, A | B):
              return
          expr = a
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `isinstance negative narrowing with union operator containing variable does not narrow`() = test("""
      class A:
          pass

      class B:
          pass

      def test(a: A | B | int, b: type[B]):
          if isinstance(a, A | b):
              return
          expr = a
      #   └ TYPE A | B | int
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `issubclass negative narrowing with class reference narrows`() = test("""
      class A:
          pass

      def test(a: type[A] | type[int]):
          if issubclass(a, A):
              return
          expr = a
      #   └ TYPE type[int]
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `issubclass negative narrowing with variable does not narrow`() = test("""
      class A:
          pass

      def test(a: type[A | int], b: type[A]):
          if issubclass(a, b):
              return
          expr = a
      #   └ TYPE type[A | int]
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `issubclass negative narrowing with tuple of classes narrows`() = test("""
      class A:
          pass

      class B:
          pass

      def test(a: type[A] | type[B] | type[int]):
          if issubclass(a, (A, B)):
              return
          expr = a
      #   └ TYPE type[int]
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `issubclass negative narrowing with tuple containing variable does not narrow`() = test("""
      class A:
          pass

      class B:
          pass

      def test(a: type[A] | type[B] | type[int], b: type[B]):
          if issubclass(a, (A, b)):
              return
          expr = a
      #   └ TYPE type[A | B | int]
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `issubclass negative narrowing with union operator narrows`() = test("""
      class A:
          pass

      class B:
          pass

      def test(a: type[A] | type[B] | type[int]):
          if issubclass(a, A | B):
              return
          expr = a
      #   └ TYPE type[int]
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `issubclass negative narrowing with union operator containing variable does not narrow`() = test("""
      class A:
          pass

      class B:
          pass

      def test(a: type[A] | type[B] | type[int], b: type[B]):
          if issubclass(a, A | b):
              return
          expr = a
      #   └ TYPE type[A | B | int]
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `negative isinstance narrows through nested tuple of classes`() = test("""
      class A:
          pass

      class B:
          pass

      def test(a: A | B | int):
          if not isinstance(a, (((A,), (B,)),)):
              expr = a
      #       └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `negative isinstance narrows through tuple with union of classes`() = test("""
      class A:
          pass

      class B:
          pass

      class C:
          pass

      def test(a: A | B | C | int):
          if not isinstance(a, (A | B, C)):
              expr = a
      #       └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-83370"])
    fun `negative isinstance with non-class literals does not narrow`() = test("""
      class A:
          pass

      def test(a: A | int):
          if not isinstance(a, (1, 2)):
              expr = a
      #       └ TYPE A | int
      """)

    @Test
    fun `NoReturn branch narrows isinstance`() = test("""
      from typing import NoReturn

      class Foo:
          def stop(self) -> NoReturn:
              raise RuntimeError('no way')

      class Bar:
          ...

      def foo(x):
          f = Foo()
          if not isinstance(x, Bar):
              f.stop()
          expr = x
      #   └ TYPE Bar
      """)

    @Test
    @TestFor(issues = ["PY-84524"])
    fun `callable narrows`() = test("""
      a = object()
      if callable(a):
          expr = a
      #   └ TYPE (...) -> object
      """)

    @Test
    @TestFor(issues = ["PY-83339"])
    fun `assert narrows optional after assert`() = test("""
      def foo(param: int | None):
          assert param
          expr = param
      #   └ TYPE int
      """)

    @Test
    fun `comprehension if clause narrows`() = test("""
      messages = ["a", None, "b"]
      ((expr := msg) for msg in messages if msg)
      #   └ TYPE str
      """)
  }

  @Nested
  inner class IntFloatAndDisjointBaseIsinstanceNarrowing {
    @Test
    fun `not isinstance float narrows int`() = test("""
      if not isinstance((x := 42), float):
          expr = x
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-83206"])
    fun `float literal is just float`() = test("""
      a = .0
      if isinstance(a, int):
          expr = a
      #   └ TYPE Never
      """)

    @Test
    @TestFor(issues = ["PY-83206"])
    fun `int float tower isinstance never`() = test("""
      def foo(y: int | float) -> None:
          if isinstance(y, float):
              if isinstance(y, int):
                  expr = y
      #           └ TYPE Never
      """)

    @Test
    @TestFor(issues = ["PY-83206"])
    fun `disjoint base decorator makes classes disjoint`() = test("""
      from typing_extensions import disjoint_base

      @disjoint_base
      class A:
          pass

      @disjoint_base
      class B:
          pass

      def foo(x: A) -> None:
          if isinstance(x, B):
              expr = x
      #       └ TYPE Never
      """)

    @Test
    @TestFor(issues = ["PY-83206"])
    fun `children of same disjoint base can intersect`() = test("""
      from typing_extensions import disjoint_base

      @disjoint_base
      class Base:
          pass

      class Child1(Base):
          pass

      class Child2(Base):
          pass

      def foo(x: Child1) -> None:
          if isinstance(x, Child2):
              expr = x
      #       └ TYPE Child1 & Child2
      """)

    @Test
    @TestFor(issues = ["PY-83206"])
    fun `slots make classes disjoint`() = test("""
      class A:
          __slots__ = ['x']

      class B:
          __slots__ = ['y']

      def foo(x: A) -> None:
          if isinstance(x, B):
              expr = x
      #       └ TYPE Never
      """)

    @Test
    @TestFor(issues = ["PY-83206"])
    fun `empty slots are not disjoint`() = test("""
      class A:
          __slots__ = []

      class B:
          __slots__ = []

      def foo(x: A) -> None:
          if isinstance(x, B):
              expr = x
      #       └ TYPE A & B
      """)

    @Test
    @TestFor(issues = ["PY-83206"])
    fun `dataclass slots create disjoint base`() = test("""
      from dataclasses import dataclass

      @dataclass(slots=True)
      class A:
          x: int

      @dataclass(slots=True)
      class B:
          y: str

      def foo(a: A) -> None:
          if isinstance(a, B):
              expr = a
      #       └ TYPE Never
      """)

    @Test
    @TestFor(issues = ["PY-83206"])
    fun `union with disjoint class filters correctly`() = test("""
      from typing_extensions import disjoint_base

      @disjoint_base
      class A:
          pass

      class B:
          pass

      class C:
          pass

      def foo(x: A | B | C) -> None:
          if isinstance(x, str):
              expr = x
      #       └ TYPE (B & str) | (C & str)
      """)
  }

  @Nested
  inner class StructuralTypeNarrowing {
    @Test
    @TestFor(issues = ["PY-85030"])
    fun `structural attribute access after narrowing and reassignment in if`() = test("""
      def f(p):
          if isinstance(p, int):
              p = "foo"
          x = p.lower()
      expr = f
      #└ TYPE (p: Any) -> None
      """)

    @Test
    @TestFor(issues = ["PY-86653"])
    fun `structural attribute access after narrowing in match`() = test("""
      def patmat(p):
          match p:
              case str():
                  p.upper()
          p.attr
      expr = patmat
      #└ TYPE (p: {attr}) -> None
      """)

    @Test
    @TestFor(issues = ["PY-86653"])
    fun `structural attribute access after narrowing in conditional`() = test("""
      def conditional(p):
          x = p.upper() if isinstance(p, str) else "bar"
          p.attr
      expr = conditional
      #└ TYPE (p: {attr}) -> None
      """)
  }

  @Nested
  inner class BitwiseOrUnionAndWalrusIsinstanceNarrowing {
    @Test
    @TestFor(issues = ["PY-44974"])
    fun `bitwise or union isinstance`() = test("""
      class A: pass
      class B(A): pass
      a = A()
      if isinstance(a, str | dict | B):
          expr = a
      #   └ TYPE B
      """)

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `bitwise or union issubclass`() = test("""
      class A: pass
      class B(A): pass
      a = A
      if issubclass(a, str | dict | B):
          expr = a
      #   └ TYPE type[B]
      """)

    @Test
    @TestFor(issues = ["PY-79861"])
    fun `walrus issubclass`() = test("""
      class A: pass
      class B(A): pass
      if issubclass(a := A, str | dict | B):
          expr = a
      #   └ TYPE type[B]
      """)

    @Test
    @TestFor(issues = ["PY-79861"])
    fun `walrus callable`() = test("""
      if callable(a := 42):
          expr = a
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `bitwise or union isinstance with int and None`() = test("""
      class A: pass
      class B(A): pass
      a = A()
      if isinstance(a, B | None):
          expr = a
      #   └ TYPE B
      """)

    @Test
    @TestFor(issues = ["PY-79861"])
    fun `walrus isinstance`() = test("""
      class A: pass
      class B(A): pass
      if isinstance((a := A()), B):
          expr = a
      #   └ TYPE B
      """)

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `bitwise or union isinstance with union in tuple`() = test("""
      from typing import Literal
      a: Literal[42] = 42
      if isinstance(a, (str, (list | dict), int | None)):
          expr = a
      #   └ TYPE Literal[42]
      """)

    @Test
    @TestFor(issues = ["PY-44974"])
    fun `bitwise or union of unions isinstance`() = test("""
      from typing import Union, Literal
      a: Literal[42] = 42
      if isinstance(a, Union[dict, Union[str, Union[int, list]]]):
          expr = a
      #   └ TYPE Literal[42]
      """)
  }

  @Nested
  inner class TypeGuardTypeIs {
    @Test
    fun `TypeGuard narrows`() = test("""
      from typing import List
      from typing import TypeGuard


      def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
          return all(isinstance(x, str) for x in val)


      def func1(val: List[object]):
          if is_str_list(val):
              expr = val
      #       └ TYPE list[str]
      """)

    @Test
    @TestFor(issues = ["PY-75961"])
    fun `TypeGuard not applied for unresolved type`() = test("""
      from typing import List
      from typing import TypeGuard

      def is_str_list(val: List[object]) -> TypeGuard[Unresolved]: # ERROR Unresolved reference 'Unresolved'
          return all(isinstance(x, str) for x in val)

      def func1(val: List[object]):
          if is_str_list(val):
              expr = val
      #       └ TYPE list[object]
      """)

    @Test
    fun `TypeGuard result is assigned`() = test("""
      from typing import List
      from typing import TypeGuard

      def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
          return all(isinstance(x, str) for x in val)


      def func1(x, val: List[object]):
          b = is_str_list(val)
          if x and b:
              expr = val
      #       └ TYPE list[str]
      """)

    @Test
    fun `TypeGuard result is assigned but val is reassigned`() = test("""
      from typing import List
      from typing import TypeGuard

      def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
          return all(isinstance(x, str) for x in val)


      def func1(x, val: List[object]):
          b = is_str_list(val)
          val = 1
          if x and b:
              expr = val
      #       └ TYPE int
      """)

    @Test
    fun `TypeGuard result is assigned but val is reassigned sometimes`() = test("""
      from typing import List
      from typing import TypeGuard

      def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
          return all(isinstance(x, str) for x in val)


      def func1(x, val: List[object]):
          b = is_str_list(val)
          if x:
              val = 1
          if b:
              expr = val
      #       └ TYPE list[str] | int
      """)

    @Test
    fun `TypeGuard presentation`() = test("""
      from typing import List
      from typing import TypeGuard


      def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
          return all(isinstance(x, str) for x in val)


      def func1(val: List[object]):
          expr = is_str_list(val)
      #       └ TYPE (val: list[object]) -> None FIXME TypeGuard[list[str]]
      """)

    @Test
    @Disabled("python.type.any: TypeGuard call presentation; with PyAnyType disabled it degrades to the function type, with it enabled inference hits a null-type guard")
    fun `TypeGuard presentation (py-any)`() = test(
      TestOptions(enablePyAnyType = true, assertRecursionPrevention = false),
      """
      from typing import List
      from typing import TypeGuard


      def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
          return all(isinstance(x, str) for x in val)


      def func1(val: List[object]):
          expr = is_str_list(val)
      #       └ TYPE TypeGuard[list[str]]
      """,
    )

    @Test
    fun `TypeIs presentation`() = test("""
      from typing import List
      from typing_extensions import TypeIs


      def is_str_list(val: List[object]) -> TypeIs[List[str]]: # WARNING Return type of TypeIs 'list[str]' is not consistent with the type of the first parameter 'list[object]'
          return all(isinstance(x, str) for x in val)


      def func1(val: List[object]):
          expr = is_str_list(val)
      #       └ TYPE (val: list[object]) -> None FIXME TypeIs[list[str]]
      """)

    @Test
    @Disabled("python.type.any: TypeIs call presentation; with PyAnyType disabled it degrades to the function type, with it enabled inference hits a null-type guard")
    fun `TypeIs presentation (py-any)`() = test(
      TestOptions(enablePyAnyType = true, assertRecursionPrevention = false),
      """
      from typing import List
      from typing_extensions import TypeIs


      def is_str_list(val: List[object]) -> TypeIs[List[str]]:
          return all(isinstance(x, str) for x in val)


      def func1(val: List[object]):
          expr = is_str_list(val)
      #       └ TYPE TypeIs[list[str]]
      """,
    )

    @Test
    fun `TypeGuard is erased on return`() = test("""
      from typing import List
      from typing_extensions import TypeIs

      def is_str_list(val: List[object]) -> TypeIs[List[str]]: # WARNING Return type of TypeIs 'list[str]' is not consistent with the type of the first parameter 'list[object]'
          return all(isinstance(x, str) for x in val)

      def func1(val: List[object]):
          return is_str_list(val)

      expr = func1([])
      #└ TYPE bool
      """)

    @Test
    fun `type alias with TypeIs`() = test("""
      from typing import List
      from typing_extensions import TypeIs

      MyTypeIs = TypeIs[List[str]]

      def is_str_list(val: List[object]) -> MyTypeIs: # WARNING Return type of TypeIs 'list[str]' is not consistent with the type of the first parameter 'list[object]'
          return all(isinstance(x, str) for x in val)

      def func1(val: List[object]):
          if is_str_list(val):
              expr = val
      #       └ TYPE list[object] & list[str]
      """)

    @Test
    fun `type alias with generic TypeIs`() = test("""
      from typing import List
      from typing_extensions import TypeIs

      type MyTypeIs[T] = TypeIs[T]

      def is_str_list(val: List[object]) -> MyTypeIs[List[str]]: # WARNING Return type of TypeIs 'list[str]' is not consistent with the type of the first parameter 'list[object]'
          return all(isinstance(x, str) for x in val)

      def func1(val: List[object]):
          if is_str_list(val):
              expr = val
      #       └ TYPE list[object] & list[str]
      """)

    @Test
    fun `TypeIs with generics`() = test("""
      from typing_extensions import TypeIs
      from typing import TypeVar

      T = TypeVar("T")

      def is_two_element_tuple(val: tuple[T, ...]) -> TypeIs[tuple[T, T]]:
          return len(val) == 2


      def func7(names: tuple[str, ...]):
          if is_two_element_tuple(names):
              expr = names
      #       └ TYPE tuple[str, str]
      """)

    @Test
    fun `TypeGuard in string literal`() = test("""
      from typing import List
      from typing import TypeGuard


      def is_str_list(val: List[object]) -> "TypeGuard[List[str]]":
          return all(isinstance(x, str) for x in val)


      def func1(val: List[object]):
          if is_str_list(val):
              expr = val
      #       └ TYPE list[str]
      """)

    @Test
    fun `TypeGuard type is not changed in else branch`() = test("""
      from typing import List
      from typing import TypeGuard


      def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
          return all(isinstance(x, str) for x in val)


      def func1(val: List[object]):
          if is_str_list(val):
              pass
          else:
              expr = val
      #       └ TYPE list[object]
      """)

    @Test
    fun `TypeGuard negation narrows in else`() = test("""
      from typing import List
      from typing import TypeGuard


      def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
          return all(isinstance(x, str) for x in val)


      def func1(val: List[object]):
          if not is_str_list(val):
              pass
          else:
              expr = val
      #       └ TYPE list[str]
      """)

    @Test
    fun `TypeGuard not changed in negated if branch`() = test("""
      from typing import List
      from typing import TypeGuard


      def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
          return all(isinstance(x, str) for x in val)


      def func1(val: List[object]):
          if not is_str_list(val):
              expr = val
      #       └ TYPE list[object]
          else:
              pass
      """)

    @Test
    fun `TypeGuard double check`() = test("""
      from typing import TypeGuard
      class Person(TypedDict): # ERROR Unresolved reference 'TypedDict'
          name: str
          age: int

      def is_person(val: dict) -> TypeGuard[Person]:
          try:
              return isinstance(val["name"], str) and isinstance(val["age"], int)
          except KeyError:
              return False


      def print_age(val: dict, val2: dict):
          if is_person(val) and is_person(val2):
              expr = val
      #       └ TYPE Person
          else:
              print("Not a person!")
      """)

    @Test
    fun `TypeIs in callable parameter narrows else branch`() = test("""
      from typing import Callable
      from typing import assert_type
      from typing_extensions import TypeIs

      def takes_narrower(x: int | str, narrower: Callable[[object], TypeIs[int]]):
          if narrower(x):
              pass
          else:
              expr = x
      #       └ TYPE str
      """)

    @Test
    fun `TypeGuard double check negation`() = test("""
      from typing import TypeGuard
      class Person(TypedDict): # ERROR Unresolved reference 'TypedDict'
          name: str
          age: int


      def is_person(val: dict) -> TypeGuard[Person]:
          try:
              return isinstance(val["name"], str) and isinstance(val["age"], int)
          except KeyError:
              return False


      def print_age(val: dict, val2: dict):
          if not is_person(val) or not is_person(val2):
              print("Not a person!");
          else:
              expr = val
      #       └ TYPE Person
      """)

    @Test
    fun `failed TypeGuard check does not affect original type`() = test("""
      from typing import List
      from typing import TypeGuard

      def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
          return all(isinstance(x, str) for x in val)


      def func1(val: List[int] | List[str]):
          if not is_str_list(val): # WARNING Expected type 'list[object]', got 'list[int] | list[str]' instead
              expr = val
      #       └ TYPE list[int] | list[str]
          else:
              pass
      """)

    @Test
    fun `failed TypeIs check does affect original type`() = test("""
      from typing import List
      from typing_extensions import TypeIs

      def is_str_list(val: List[object]) -> TypeIs[List[str]]: # WARNING Return type of TypeIs 'list[str]' is not consistent with the type of the first parameter 'list[object]'
          return all(isinstance(x, str) for x in val)

      def func1(val: List[int] | List[str]):
          if not is_str_list(val): # WARNING Expected type 'list[object]', got 'list[int] | list[str]' instead
              expr = val
      #       └ TYPE list[int]
          else:
              pass
      """)

    @Test
    fun `TypeIs narrows in if branch`() = test("""
      from typing import Any, Callable, Literal, Mapping, Sequence, TypeVar, Union
      from typing_extensions import TypeIs


      def is_str1(val: Union[str, int]) -> TypeIs[str]:
          return isinstance(val, str)


      def func1(val: Union[str, int]):
          if is_str1(val):
              expr = val
      #       └ TYPE str
          else:
              pass
      """)

    @Test
    fun `TypeIs narrows in else branch`() = test("""
      from typing import Any, Callable, Literal, Mapping, Sequence, TypeVar, Union
      from typing_extensions import TypeIs


      def is_str1(val: Union[str, int]) -> TypeIs[str]:
          return isinstance(val, str)


      def func1(val: Union[str, int]):
          if is_str1(val):
              pass
          else:
              expr = val
      #       └ TYPE int
      """)

    @Test
    fun `TypeIs narrows union of lists`() = test("""
      from typing import Any, Callable, Literal, Mapping, Sequence, TypeVar, Union
      from typing_extensions import TypeIs

      def is_list(val: object) -> TypeIs[list[Any]]:
          return isinstance(val, list)


      def func3(val: dict[str, str] | list[str] | list[int] | Sequence[int]):
          if is_list(val):
              expr = val
      #       └ TYPE list[str] | list[int]
          else:
              pass
      """)

    @Test
    @TestFor(issues = ["PY-62476"])
    fun `TypeGuard return type treated as bool`() = test("""
      from typing import TypeGuard
      def foo(param: str | int) -> TypeGuard[str]:
          return param # WARNING Expected type 'TypeGuard[str]', got 'str | int' instead
      """)

    @Test
    fun `comprehension if clause narrows produces no warning`() = test("""
      messages = ["a", None, "b"]
      "".join(msg for msg in messages if msg)
      """)
  }
}

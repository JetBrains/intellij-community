// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.fixtures.PyCodeInsightTestCase.TestCaseOptions
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Cross-cutting type-inference tests that don't fit a single feature category:
 * scope/condition flow (`global`/`nonlocal`/conditional definitions), recursion guards,
 * lambdas, qualified-name resolution, subclass parameter/return inference,
 * construction/`__new__`/`__init__`, decorated methods, template strings, `Any`/`Unknown`
 * rendering, structural types, dunder-`__all__`, sentinels, and illegal type-form handling.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyInferenceMiscTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class ScopeAndConditionFlow {
    @Test
    fun `conditional definition in inner scope`() = test("""
      if something:
      #  ^^^^^^^^^ ERROR Unresolved reference 'something'
          foo = 'foo'
      else:
          foo = 0

      expr = foo
      # └ TYPE Literal["foo", 0]
      """.trimIndent())

    @Test
    fun `conditional definition in outer scope`() = test("""
      if something:
      #  ^^^^^^^^^ ERROR Unresolved reference 'something'
          foo = 'foo'
      else:
          foo = 0

      def f():
          expr = foo
      #   └ TYPE Literal["foo", 0]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-21175"])
    fun `Any added by conditional definition`() = test("""
      def f(x, y):
          if x:
              var = y
          else:
              var = 'foo'
          expr = var
      #   └ TYPE Literal["foo"] | Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-37755"])
    fun `nonlocal type from enclosing function`() = test("""
      def fun():
          expr = True
      #   └ TYPE Literal[True]

          def nuf():
              nonlocal expr
              expr
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-37755"])
    fun `nonlocal reads enclosing assignment over module global`() = test("""
      a = []

      def fun():
          a = True

          def nuf():
              nonlocal a
              expr = a
      #       └ TYPE Literal[True]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-37755"])
    fun `nonlocal reads union of enclosing assignments`() = test("""
      a = []

      def fun():
          if input():
              a = True
          else:
              a = 5

          def nuf():
              nonlocal a
              expr = a
      #       └ TYPE Literal[True, 5]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-82115"])
    fun `nonlocal reads from intervening function scope`() = test("""
      def outer1():
          s = "aba"

          def outer2():
              def inner1():
                  nonlocal s
                  expr = s
      #           └ TYPE Literal["aba"]

              def inner2():
                  global s
                  s = 1
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-37755"])
    fun `global read sees module-level list`() = test("""
      expr = []
      # └ TYPE list[Unknown]

      def fun():
          global expr
          expr
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-37755"])
    fun `global read from nested function sees module-level list`() = test("""
      expr = []
      #└ TYPE list[Unknown]

      def fun():
          def nuf():
              global expr
              expr
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-37755"])
    fun `global declaration shadows local of same name`() = test("""
      expr = []
      # └ TYPE list[Unknown]

      def fun():
          expr = True

          def nuf():
              global expr
              expr
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-37755"])
    fun `global read of conditionally defined module variable`() = test("""
      if input():
          a = True
      else:
          a = 5

      def fun():
          def nuf():
              global a
              expr = a
      #       └ TYPE Literal[True, 5]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-82115"])
    fun `global read of unbound name is Any`() = test("""
      def outer():
          s = "aba"

          def inner():
              global s
              expr = s
      #       └ TYPE Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-82115"])
    fun `global read sees sibling global assignment`() = test("""
      def outer():
          s = "aba"

          def inner1():
              global s
              s = 1

          def inner2():
              global s
              expr = s
      #       └ TYPE Literal[1]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-18217"])
    fun `conditional import resolved in outer scope`() = test("""
      if something:
      #  ^^^^^^^^^ ERROR Unresolved reference 'something'
          from m1 import foo
      else:
          from m2 import foo

      def f():
          expr = foo
      #   └ TYPE str | int
      """.trimIndent(),
      "m1.py" to """
        foo = 'foo'
        ''':type: str'''
        """.trimIndent(),
      "m2.py" to """
        foo = 0
        ''':type: int'''
        """.trimIndent())

    @Test
    @TestFor(issues = ["PY-18402"])
    fun `condition in imported module yields union`() = test("""
      from m1 import foo

      def f():
          expr = foo
      #   └ TYPE int | str
      """.trimIndent(),
      "m1.py" to """
        if something:
            foo = 'foo'
            ''':type: str'''
        else:
            foo = 0
            ''':type: int'''
        """.trimIndent())
  }

  @Nested
  inner class RecursionAndFixedPointLoops {
    @Test
    @TestFor(issues = ["PY-76659"])
    fun `recursive resolve in while loop`() = test("""
      x = 42
      while x:
          x = x + 1
      expr = x
      #└ TYPE Literal[42] | int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-76659"])
    fun `recursive resolve in while with branch`() = test("""
      x = 42
      b: bool = ...
      #         ^^^ WARNING Expected type 'bool', got 'EllipsisType' instead
      while x:
          if b:
              x = x + 1
              expr = x
      #       └ TYPE int
          else:
              x = x - 1
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-76659"])
    fun `class chain fixed point in while loop`() = test("""
      class A:
          def bar(self) -> "B":
              return B()
      class B:
          def bar(self) -> "C":
              return C()
      class C:
          def bar(self) -> "D":
              return D()
      class D:
          def bar(self) -> A:
              return A()

      def foo(b):
          x = A()
          while b:
              x = x.bar()

          expr = x
      #   └ TYPE A | B | C | D
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-76659"])
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `types in loop compute fast`() = test("""
      def is_empty(x: int, y: int) -> bool:
          ...

      def drop_grain() -> None:
          x, y = 500, 0

          while True:
              if is_empty(x, y):
                  x, y = x + 1, y
              elif is_empty(x, y):
                  x, y = x, y
              elif is_empty((expr := x), y):
      #                      └ TYPE Literal[500] | int
                  x, y = x, y
              elif not is_empty(x, y):
                  break
      """.trimIndent())

    @Test
    @TestFor(issues = ["EA-40207"])
    fun `recursion through self-referential list`() = test("""
      def f():
          return [f()]
      expr = f()
      #└ TYPE list[list[Unknown]]
      """.trimIndent())

    @Test
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `stack overflow prevented on recursive call`() = test("""
      def foo(x): return foo(x)
      expr = foo(1)
      #└ TYPE Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-73958"])
    fun `no stack overflow on many method calls`() = test("""
      class Foo:
          def foo(self):
              pass
      
      xxx = Foo()
      
      ${"xxx.foo()\n      ".repeat(1000)}
      
      expr = xxx
      #└ TYPE Foo
      """.trimIndent())
  }

  @Nested
  inner class Lambdas {
    @Test
    @TestFor(issues = ["PY-28130"])
    fun `lambda as non-annotated function return value`() = test("""
      def f():
          return lambda x: x + 1
      expr = f()
      #└ TYPE (x: Unknown) -> UnsafeUnion[int, Unknown]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-28130"])
    fun `lambda as non-annotated parameter value`() = test("""
      from typing import Callable

      def f(fn): ...

      f(lambda expr: 42)
      #        └ TYPE Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-28130"])
    fun `lambda as non-annotated variable value`() = test("""
      t = lambda x: x + 1
      expr = t
      #└ TYPE (x: Unknown) -> UnsafeUnion[int, Unknown]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-28130"])
    fun `lambda parameter does not endless recursion`() = test("""
      _ = lambda expr: expr
      #          └ TYPE Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-28130"])
    fun `lambda parameter uses parameter context`() = test("""
      from typing import Callable

      def f(fn: Callable[[int], object]): ...

      f(lambda expr: expr)
      #        └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-28130"])
    fun `lambda parameter uses return context`() = test("""
      from typing import Callable

      def f() -> Callable[[int], object]:
        return lambda expr: expr
      #                       └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-28130"])
    fun `lambda uses generic context`() = test("""
      from typing import Callable

      def f[T](t: T, fn: Callable[[T], object]) -> T: ...

      f(1, lambda expr: expr)
      #                     └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-28130"])
    fun `lambda uses generic context receiver`() = test("""
      from typing import Callable

      class A[T]:
          def f(self, fn: Callable[[T], object]) -> T: ...

      A[int]().f(lambda expr: expr)
      #                         └ TYPE int
      """.trimIndent())
  }

  @Nested
  inner class QualifiedNameResolution {
    @Test
    fun `incomplete qualified name clashes with local variable`() = test("""
      class MyClass:
          foo = 'spam'

      def f(foo):
          _ = foo.illegal
          expr = MyClass.foo
      #   └ TYPE str
      """.trimIndent())

    @Test
    fun `qualified name resolution prefers self attribute over reassignment`() = test("""
      class C:
          def m(self):
              self.t = 5

      def f(self: C, x: float):
          self.t = "foo"
          expr = self.t
      #   └ TYPE Literal["foo"]
      """.trimIndent())

    @Test
    fun `qualified name resolution uses annotated attribute set in method`() = test("""
      class C:
          def m(self):
              self.t: int = 5

      def f(self: C, x: float):
          self.t = "foo"
      #            ^^^^^ WARNING Expected type 'int', got 'Literal["foo"]' instead
          expr = self.t
      #   └ TYPE int
      """.trimIndent())

    @Test
    fun `qualified name resolution uses annotated attribute set in init`() = test("""
      class C:
          def __init__(self):
              self.t: int = 5

      def f(self: C, x: float):
          self.t = "foo"
      #            ^^^^^ WARNING Expected type 'int', got 'Literal["foo"]' instead
          expr = self.t
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-74257"])
    fun `not properly imported qualified name in type hint is Any`() = test("""
      import pkg
      #      ^^^ ERROR No module named 'pkg'

      def f() -> "pkg.subpkg.mod.MyClass": ...

      expr = f()
      #└ TYPE Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-89253"])
    fun `qualified subscription annotation does not use builtin alias workaround`() = test("""
      from sample import A

      a = A()
      expr = a.b
      #└ TYPE Unknown
      """.trimIndent(),
      "mod.py" to """
        class dict:
            def __class_getitem__(cls, item):
                return int
        """.trimIndent(),
      "sample.py" to """
        from mod import dict

        class Base:
            def dict(self):
                return None

        class A(Base):
            b: dict[int, str]
        """.trimIndent())
  }

  @Nested
  inner class SubclassParameterAndReturnInference {
    @Test
    fun `parameter type inference in subclass from annotation`() = test("""
      class Base:
          def test(self, param: int) -> int: pass

      class Subclass(Base):
          def test(self, param):
              expr = param
      #       └ TYPE int
      """.trimIndent())

    @Test
    fun `parameter type inference through one base`() = test("""
      class Base:
          def test(self, param: int) -> int: pass

      class Base1(Base):
          pass

      class Subclass(Base1):
          def test(self, param):
              expr = param
      #       └ TYPE int
      """.trimIndent())

    @Test
    fun `parameter type inference first base wins`() = test("""
      class Base1:
          def test(self, param: int) -> int: pass

      class Base2:
          def test(self, param: str) -> str: pass

      class Subclass(Base1, Base2):
          def test(self, param):
              expr = param
      #       └ TYPE int
      """.trimIndent())

    @Test
    fun `parameter type inference through MRO`() = test("""
      class Base1:
          def test(self, param: int) -> int: pass

      class Base2:
          def test(self, param: str) -> str: pass

      class Base3(Base1):
          pass

      class Subclass(Base3, Base2):
          def test(self, param):
              expr = param
      #       └ TYPE int
      """.trimIndent())

    @Test
    fun `parameter type inference C3 MRO with diamond`() = test("""
      class Base1:
          def test(self, param: str) -> str: pass

      class Base2(Base1):
          def test(self, param: int) -> int: pass
      #           │                     ^^^ WARNING Return type of method 'Base2.test()' does not match return type the base method in class 'Base1'
      #           ^^^^^^^^^^^^^^^^^^ WARNING Signature of method 'Base2.test()' does not match signature of the base method in class 'Base1'

      class Base3(Base1):
          pass

      class Subclass(Base3, Base2):
          def test(self, param):
              expr = param
      #       └ TYPE int
      """.trimIndent())

    @Test
    fun `parameter type inference through unannotated override`() = test("""
      class Base1:
          def test(self, param: int) -> int: pass

      class Base2(Base1):
          def test(self, param): pass

      class Subclass(Base2):
          def test(self, param):
              expr = param
      #       └ TYPE int
      """.trimIndent())

    @Test
    fun `parameter type inference in static methods`() = test("""
      class Base:
          @staticmethod
          def test(param: int, param1: int) -> int: pass

      class Subclass(Base):
          @staticmethod
          def test(param, param1):
              expr = param
      #       └ TYPE int
      """.trimIndent())

    @Test
    fun `return type inference in subclass from annotation`() = test("""
      class Base:
          def test(self) -> int: pass

      class Subclass(Base):
          def test(self): pass

      expr = Subclass().test()
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `return type inference through hierarchy from annotation`() = test("""
      class Base:
          def test(self) -> int: pass

      class Base1(Base):
          pass

      class Subclass(Base1):
          def test(self): pass

      expr = Subclass().test()
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-87329"])
    fun `inherited method return type does not change to subclass`() = test("""
      class A:
          def foo(self) -> A:
              return A()

      class B(A):
          ...

      expr = B().foo()
      #└ TYPE A
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-87329"])
    fun `inherited generic method return type does not change to subclass`() = test("""
      class A[T]:
          def foo(self) -> A[T]:
              return A()

      class B[T](A[T]):
          ...

      expr = B[int]().foo()
      #└ TYPE A[int]
      """.trimIndent())
  }

  @Nested
  inner class ConstructionNewAndInit {
    @Test
    fun `class __new__ result`() = test("""
      class C(object):
          def __new__(cls):
              self = object.__new__(cls)
              self.foo = 1
              return self

      expr = C()
      #└ TYPE C
      """.trimIndent())

    @Test
    fun `object __new__ result`() = test("""
      class C(object):
          def __new__(cls):
              expr = object.__new__(cls)
      #       └ TYPE Self@C
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-44470"])
    fun `inferring and matching cls in __new__`() = test("""
      class Subclass(dict):
          def __new__(cls, *args, **kwargs):
              expr = super().__new__(cls, *args, **kwargs)
              return expr
      #              └ TYPE dict FIXME Self@Subclass
      """.trimIndent())

    @Test
    fun `constructing generic class with not filled generic value`() = test("""
      from typing import Iterator
      
      class MyIterator(Iterator[int]):
      #     ^^^^^^^^^^ WEAK-WARNING Class MyIterator must implement all abstract methods
          def __init__(self) -> None:
              self.other = "other"
      expr = MyIterator()
      #│     ^^^^^^^^^^^^ WARNING Cannot instantiate abstract class 'MyIterator'
      #└ TYPE MyIterator
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26992"])
    fun `initializing inner callable class`() = test("""
      class A:
          class B:
              def __init__(self):
                  pass
              def __call__(self, x):
                  pass
          def __init__(self):
              pass
      expr = A.B()
      # └ TYPE B
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26992", "PY-87449"])
    fun `initializing inner callable class through explicit dunder init`() = test("""
      class A:
          class B:
              def __init__(self):
                  pass
              def __call__(self, x):
                  pass
          def __init__(self):
              pass
      expr = A.B.__init__(object.__new__(A.B))
      #└ TYPE None
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26992"])
    fun `initializing inner callable class through explicit dunder new`() = test("""
      class A(object):
          class B(object):
              def __init__(self):
                  pass
              def __call__(self, x):
                  pass
          def __init__(self):
              pass
      expr = A.B.__new__(A.B)
      #└ TYPE B
      """.trimIndent())

    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON37, assertRecursionPrevention = false)
    @TestFor(issues = ["PY-27913"])
    fun `dunder class getitem first parameter`() = test("""
      class Foo:
          def __class_getitem__(cls, item):
              expr = cls
      #       └ TYPE Type[Self@Foo]
      """.trimIndent())

    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON36, assertRecursionPrevention = false)
    @TestFor(issues = ["PY-25545"])
    fun `dunder init subclass first parameter`() = test("""
      class Foo:
          def __init_subclass__(cls):
              expr = cls
      #       └ TYPE Type[Self@Foo]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-4279"])
    fun `field reassignment narrows to most recent`() = test("""
      class C1(object):
          def m1(self):
              pass

      class C2(object):
          def m2(self):
              pass

      class Test(object):
          def __init__(self, param1):
              self.x = param1
              self.x = C1()
              expr = self.x
      #       └ TYPE C1
      """.trimIndent())

    @Test
    fun `calling union of dataclasses`() = test("""
      from dataclasses import dataclass

      @dataclass
      class DC1:
          x: int = 0

      @dataclass
      class DC2:
          y: str = ""

      def f(cls: type[DC1] | type[DC2]):
          expr = cls()
      #   └ TYPE DC1 | DC2
      """.trimIndent())
  }

  @Nested
  inner class DecoratedMethodsAndClassDecorators {
    @Test
    @TestFor(issues = ["PY-51321"])
    fun `class decorated function`() = test("""
      class A:
          def __init__(self, fn): ...

      @A
      def bar(): ...

      expr = bar
      #└ TYPE A
      """.trimIndent())

    @Test
    fun `decorated method on class`() = test("""
      from typing import Callable

      def dec[T](f: Callable[[T, bool], bool]) -> Callable[[T, bool], bool]:
          def a(b: bool) -> bool:
              return f(A(), b)
      #                ^^^ WARNING FIXME Expected 'T', got 'A' instead
          return a
      #          └ WARNING Expected type '(T, bool) -> bool', got '(b: bool) -> bool' instead

      class A:
          @dec
          def f(self, a: bool) -> bool:
              return True

      a = A()

      expr = a.f
      #└ TYPE (bool) -> bool
      """.trimIndent())

    @Test
    fun `static decorated method converting instance method to class method class call`() = test("""
      from typing import Callable

      def dec[T](f: Callable[[T, bool], bool]) -> Callable[[bool], bool]:
          def a(b: bool) -> bool:
              return f(A(), b)
          return a

      class A:
          @staticmethod
          @dec
          def f(self, a: bool) -> bool:
              return True

      expr = A.f
      # └ TYPE (bool) -> bool
      """.trimIndent())

    @Test
    fun `static decorated method converting instance method to class method instance call`() = test("""
      from typing import Callable

      def dec[T](f: Callable[[T, bool], bool]) -> Callable[[bool], bool]:
          def a(b: bool) -> bool:
              return f(A(), b)
          return a

      class A:
          @staticmethod
          @dec
          def f(self, a: bool) -> bool:
              return True

      expr = A().f
      #└ TYPE (bool) -> bool
      """.trimIndent())

    @Test
    fun `class method qualified with class definition`() = test("""
      class Foo:
          @classmethod
          def make_foo(cls, x: str) -> 'Foo':
              pass
      expr = Foo.make_foo
      #└ TYPE (x: str) -> Foo
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27143"])
    fun `classmethod replace definition qualified with class`() = test("""
      class Base:
          @classmethod
          def cls(cls):
              return cls
      class Derived(Base):
          pass
      expr = Derived.cls()
      #└ TYPE type[Derived]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27143"])
    fun `classmethod replace definition qualified with instance`() = test("""
      class Base:
          @classmethod
          def cls(cls):
              return cls
      class Derived(Base):
          pass
      expr = Derived().cls()
      #└ TYPE type[Derived]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27143"])
    fun `method replace definition via dunder class qualified with class`() = test("""
      class Base(object):
          def cls(self):
              return self.__class__
      class Derived(Base):
          pass
      expr = Derived.cls(Derived())
      #└ TYPE type[Derived]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27143"])
    fun `method replace definition via dunder class qualified with instance`() = test("""
      class Base(object):
          def cls(self):
              return self.__class__
      class Derived(Base):
          pass
      expr = Derived().cls()
      #└ TYPE type[Derived]
      """.trimIndent())

    @Test
    fun `method replace definition via dunder class qualified with class py3`() = test("""
      class Base:
          def cls(self):
              return self.__class__
      class Derived(Base):
          pass
      expr = Derived.cls(Derived())
      #└ TYPE type[Derived]
      """.trimIndent())

    @Test
    fun `method replace definition via dunder class qualified with instance py3`() = test("""
      class Base:
          def cls(self):
              return self.__class__
      class Derived(Base):
          pass
      expr = Derived().cls()
      #└ TYPE type[Derived]
      """.trimIndent())

    @Test
    fun `static method qualified with known generics instance`() = test("""
      my_list = [1, 2, 2, 3, 3]
      expr = my_list.count
      #└ TYPE (value: int, /) -> int
      """.trimIndent())

    @Test
    fun `static method qualified with unknown generics instance`() = test("""
      my_list = []
      expr = my_list.count
      #└ TYPE (value: Unknown, /) -> int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-91216"])
    fun `decorated function assigned to a module attribute`() = test("""
      import m

      expr = m.f
      # └ TYPE int
      """.trimIndent(),
      "m.py" to """
        def _dec(fun) -> int:
            return 12

        @_dec
        def _func():
            raise NotImplementedError

        f = _func
        """.trimIndent())
  }

  @Nested
  inner class ReturnFlowTryFinallyExcept {
    @Test
    @TestFor(issues = ["PY-78964"])
    fun `function return type try finally`() = test("""
      def test():
          try:
              return 42
          finally:
              return "str"
      #       ^^^^^^^^^^^^ ERROR Python version 3.15 does not support 'return' inside 'finally' clause

          return True

      expr = test()
      #└ TYPE Literal["str"]
      """.trimIndent())

    @Test
    fun `shadowing return inside finally`() = test("""
      def f():
          try:
              return 42
          finally:
              return "foo"
      #       ^^^^^^^^^^^^ ERROR Python version 3.15 does not support 'return' inside 'finally' clause
      expr = f()
      #└ TYPE Literal["foo"]
      """.trimIndent())

    @Test
    fun `non-shadowing return inside finally`() = test("""
      def f(p):
          try:
              return 42
          finally:
              if p:
                  return "foo"
      #           ^^^^^^^^^^^^ ERROR Python version 3.15 does not support 'return' inside 'finally' clause
      expr = f()
      #│       └ WARNING Parameter 'p' unfilled
      #└ TYPE Literal["foo", 42]
      """.trimIndent())

    @Test
    fun `return inside except else`() = test("""
      def f(p):
          try:
              e1()
      #       ^^ ERROR Unresolved reference 'e1'
          except Exception:
              return "foo"
          else:
              return True
          finally:
              pass
      expr = f()
      #│       └ WARNING Parameter 'p' unfilled
      #└ TYPE Literal["foo", True]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-83644"])
    fun `call of callable attribute returning Never terminates flow`() = test("""
      from typing import Callable, Never, Protocol

      class _WithException[F, ET](Protocol):
          __call__: F
          Exception: ET

      class Failed(BaseException): ...

      fail: _WithException[Callable[[str, bool], Never], type[Failed]]

      def f() -> int:
          try:
              return 1
          except TimeoutError:
              expr = fail("boom", True)
      #       └ TYPE Never
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-52930"])
    fun `exception group in except star`() = test("""
      try:
          raise ExceptionGroup("asdf", [Exception("fdsa")])
      except* Exception as expr:
      #                    └ TYPE ExceptionGroup
          pass
      """.trimIndent())

    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27, assertRecursionPrevention = false)
    fun `except type with python2 comma syntax`() = test("""
      try:
          pass
      except ImportError, expr:
      #                   └ TYPE ImportError
          pass
      """.trimIndent())
  }

  @Nested
  inner class ElifIsinstanceNarrowingFlow {
    @Test
    fun `elif narrows out leading branches`() = test("""
      class A:
          pass

      def foo(a: int | str | A):
          if isinstance(a, A):
              pass
          elif isinstance(a, int):
              pass
          else:
              expr = a
      #       └ TYPE str
      """.trimIndent())

    @Test
    fun `elif with negated isinstance narrows`() = test("""
      class A:
          pass

      def foo(a: int | str | A):
         if isinstance(a, int):
             pass
         elif not isinstance(a, str):
             expr = a
      #      └ TYPE A
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-5614"])
    fun `known type attribute narrowed by isinstance`() = test("""
      class C(object):
          def __init__(self):
              self.foo = 42
          def f(self):
              if isinstance(self.foo, bool):
                  expr = self.foo
      #           └ TYPE bool
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-5614"])
    fun `unknown reference type attribute narrowed by isinstance`() = test("""
      def f(x):
          if isinstance(x.foo, str):
              expr = x.foo
      #       └ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-5614"])
    fun `nested unknown reference type attribute narrowed by isinstance`() = test("""
      def f(x):
          if isinstance(x.foo.bar, str):
              expr = x.foo.bar
      #       └ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-5614"])
    fun `unknown type attribute narrowed by isinstance`() = test("""
      class C(object):
          def __init__(self, foo):
              self.foo = foo
          def f(self):
              if isinstance(self.foo, str):
                  expr = self.foo
      #           └ TYPE str
      """.trimIndent())
  }

  @Nested
  inner class StructuralTypes {
    @Test
    fun `structural type from attribute and call`() = test("""
      def f(x):
          x.foo + x.bar()
          expr = x
      #   └ TYPE {foo, bar}
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-20833"])
    fun `structural type with dunder len`() = test("""
      def expand(values1):
          a = len(values1)
          expr = values1
      #   └ TYPE {__len__}
      """.trimIndent())

    @Test
    fun `no __contains__ in __contains__ argument for structural type`() = test("""
      def f(x):
         x in []
         x.foo
         x[0]
         expr = x
      #  └ TYPE {foo, __getitem__}
      """.trimIndent())

    @Test
    fun `only related nested attributes in structural type`() = test("""
      def g(x):
          x.bar

      def f(x, y):
          x.foo + g(y)
          expr = x
      #   └ TYPE {foo}
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-85030"])
    fun `structural type and definite reassignment under condition`() = test("""
      def f(p):
          if p:
              p = "foo"
          else:
              p = "bar"
          return p.lower()

      f(42)
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-85030"])
    fun `structural type and strict union`() = test("""
      responses = {
          100: "abc",
      }

      def process(status):
          if isinstance(status, int):
              status = responses[status]
          return status.lower().replace(" ", "-")

      def do(arg):
          title = "abc" if arg else 100
          return process(title)
      """.trimIndent())
  }

  @Nested
  inner class DocstringTypeForms {
    @Test
    fun `no resolve to functions in docstring types`() = test("""
      class C(object):
          def bar(self):
              pass

      def foo(x):
          '''
          :type x: C | C.bar | foo
          '''
          expr = x
      #   └ TYPE C | Unknown
      """.trimIndent())

    @Test
    fun `parameter of function type and return value from docstring`() = test("""
      def func(f):
          '''
          :type f: (unknown) -> str
          '''
          return 1

      expr = func(foo)
      #│          ^^^ ERROR Unresolved reference 'foo'
      #└ TYPE Literal[1]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-21474"])
    fun `reassigning optional list with default value from docstring`() = test("""
      def x(things):
          '''
          :type things: None | list[str]
          '''
          expr = things if things else []
      #   └ TYPE list[str] | list[Unknown]
      """.trimIndent())
  }

  @Nested
  inner class BuiltinsAndStdlibSentinels {
    @Test
    @TestFor(issues = ["PY-21350"])
    fun `builtin input`() = test("""
      expr = input()
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `builtin round int`() = test("""
      expr = round(1)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `builtin round int with ndigits`() = test("""
      expr = round(1, 1)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `builtin round float`() = test("""
      expr = round(1.1)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-13750"])
    fun `builtin round bool`() = test("""
      expr = round(True)
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `max result`() = test("""
      expr = max(1, 2, 3)
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `min result`() = test("""
      expr = min(1, 2, 3)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-21692"])
    fun `sum result`() = test("""
      expr = sum([1, 2, 3])
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-21083"])
    fun `float fromhex result`() = test("""
      expr = float.fromhex("0.5")
      #└ TYPE float
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-20409"])
    fun `get from dict with default None value`() = test("""
      d = {}
      expr = d.get("abc", None)
      #└ TYPE Unknown | None
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24383"])
    fun `subscription on weak type`() = test("""
      foo = bar() if 42 != 42 else [1, 2, 3, 4]
      #     ^^^ ERROR Unresolved reference 'bar'
      expr = foo[0]
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `set literal`() = test("""
      expr = {1, 2, 3}
      #└ TYPE set[int]
      """.trimIndent())

    @Test
    fun `open default mode is text`() = test("""
      expr = open('foo')
      #└ TYPE TextIOWrapper[_WrappedBuffer]
      """.trimIndent())

    @Test
    fun `open binary mode is buffered reader`() = test("""
      expr = open('foo', 'rb')
      #└ TYPE BufferedReader[_BufferedReaderStream]
      """.trimIndent())

    @Test
    fun `open text mode is text`() = test("""
      expr = open('foo', 'r')
      #└ TYPE TextIOWrapper[_WrappedBuffer]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35885"])
    fun `function dunder doc`() = test("""
      def example():
          '''Example Docstring'''
          return 0
      expr = example.__doc__
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-22945"])
    fun `not installed typing used in analysis`() = test("""
      from re import compile
      expr = compile("str")
      #└ TYPE Pattern[str]
      """.trimIndent())
  }

  @Nested
  inner class EllipsisAndEllipsis {
    @Test
    @TestFor(issues = ["PY-80436"])
    fun `ellipsis literal is EllipsisType`() = test("""
      expr = ...
      #└ TYPE EllipsisType
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80436"])
    fun `Ellipsis is EllipsisType`() = test("""
      expr = Ellipsis
      #└ TYPE EllipsisType
      """.trimIndent())
  }

  @Nested
  inner class BinaryOrContextManagerMiscInference {
    @Test
    @TestCaseOptions(assertRecursionPrevention = false)
    @TestFor(issues = ["PY-9662"])
    fun `binary expression with annotated Any operand left`() = test("""
      from typing import Any
      x: Any
      expr = x * 2
      #└ TYPE UnsafeUnion[int, Unknown] FIXME UnsafeUnion[int, Any]
      """.trimIndent())

    @Test
    @TestCaseOptions(assertRecursionPrevention = false)
    @TestFor(issues = ["PY-9662"])
    fun `binary expression with annotated Any operand right`() = test("""
      from typing import Any
      x: Any
      expr = 2 * x
      #└ TYPE UnsafeUnion[int, Unknown] FIXME UnsafeUnion[int, Any]
      """.trimIndent())

    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON35, assertRecursionPrevention = false)
    @TestFor(issues = ["PY-9662"])
    fun `binary expression with unknown parameter operand left`() = test("""
      def f(x):
          expr = x * 2
      #   └ TYPE UnsafeUnion[int, Unknown]
      """.trimIndent())

    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON35, assertRecursionPrevention = false)
    @TestFor(issues = ["PY-9662"])
    fun `binary expression with unknown parameter operand right`() = test("""
      def f(x):
          expr = 2 * x
      #   └ TYPE UnsafeUnion[int, Unknown]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-83348"])
    fun `or expression with optional left side`() = test("""
      def foo(x: int | None):
          expr = x or "foo"
      #   └ TYPE int | Literal["foo"]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-83348"])
    fun `or expression with None left side`() = test("""
      def foo(x: None):
          expr = x or "foo"
      #   └ TYPE Literal["foo"]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-51329"])
    fun `metaclass or shadows reflected on right`() = test("""
      class M(type):
          def __or__(self, other: object) -> int:
              return 1

      class A(metaclass=M): ...

      expr = A | str
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-29891"])
    fun `context manager type from Type ContextManager annotation`() = test("""
      from typing import Type, ContextManager
      def example():
        manager: Type[ContextManager[str]]
        with manager() as m:
              expr = m
      #         └ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-22181"])
    fun `iteration over iterable with separate iterator`() = test("""
      class AIter(object):
          def __next__(self):
              return 5
      class A(object):
          def __iter__(self):
              return AIter()
      a = A()
      for expr in a:
      #   └ TYPE Literal[5]
          print(expr)
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-37678"])
    fun `dataclasses replace returns instance type`() = test("""
      import dataclasses as dc

      @dc.dataclass
      class Foo:
          x: int
          y: int

      foo = Foo(1, 2)
      expr = dc.replace(foo, x=3)
      #└ TYPE Foo
      """.trimIndent())

    @Test
    fun `function returns None`() = test("""
      def foo(p):
          assert p
      expr = foo
      #└ TYPE (p: Unknown) -> None
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-7063"])
    fun `default parameter value`() = test("""
      def f(x, y=0):
          return y
      expr = f(a, b)
      #│       │  └ ERROR Unresolved reference 'b'
      #│       └ ERROR Unresolved reference 'a'
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-7063"])
    fun `default parameter None ignored`() = test("""
      def f(x=None):
          expr = x
      #   └ TYPE Unknown
      """.trimIndent())

    @Test
    fun `parameter of function type returns annotated return`() = test("""
      def func(f):
          '''
          :type f: (unknown) -> str
          '''
          return 1

      expr = func(foo)
      #│          ^^^ ERROR Unresolved reference 'foo'
      #└ TYPE Literal[1]
      """.trimIndent())

    @Test
    fun `function return generic callable`() = test("""
      from typing import Callable, TypeVar

      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      T3 = TypeVar('T3')

      def bar(p1: T1, p2: T2) -> Callable[[T1, T2, T3], T3]:
        pass

      expr = bar(dunno, 'sd')
      #│         ^^^^^ ERROR Unresolved reference 'dunno'
      #└ TYPE (Unknown, str, T3) -> T3
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24364"])
    fun `reassigned parameter inferred as generator function`() = test("""
      def resort(entries):
          entries = list(entries)
          entries.sort(reverse=True)
          for entry in entries:
              yield entry
      expr = resort
      #└ TYPE (entries: Unknown) -> Generator[Unknown, Unknown, None]
      """.trimIndent())

    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON311, assertRecursionPrevention = false)
    fun `return type annotation overrides body`() = test("""
      def foo(x) -> list:
          return x
      expr = foo(None)
      #└ TYPE list[Unknown]
      """.trimIndent())

    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON34, assertRecursionPrevention = false)
    fun `type annotation on parameter`() = test("""
      def foo(x: str) -> list:
      #                  ^^^^ WARNING Expected type 'List[Unknown]', got 'None' instead
          expr = x
      #   └ TYPE str
      """.trimIndent())

    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON36, assertRecursionPrevention = false)
    @TestFor(issues = ["PY-26061"])
    fun `unresolved generic replacement is Any`() = test("""
      from typing import TypeVar, Generic

      T = TypeVar('T')
      V = TypeVar('V')

      class B(Generic[T]):
          def f(self) -> T:
              ...

      class C(B[V], Generic[V]):
          pass

      expr = C().f()
      #└ TYPE Unknown
      """.trimIndent())
  }

  @Nested
  inner class TemplateStrings {
    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON314, assertRecursionPrevention = false)
    @TestFor(issues = ["PY-79967"])
    fun `interpolation expression type from template string`() = test("""
      name = "John"
      expr = t"Hello, {name}!".interpolations[0].expression
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON313, assertRecursionPrevention = false)
    @TestFor(issues = ["PY-79967"])
    fun `template string inferred as str before python314`() = test("""
      expr = t"template string"
      #│     └ ERROR Python version 3.13 does not support a 'T' prefix
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON314, assertRecursionPrevention = false)
    @TestFor(issues = ["PY-79967"])
    fun `template string inferred as Template at python314`() = test("""
      expr = t"template string"
      #└ TYPE Template
      """.trimIndent())
  }

  @Nested
  inner class TypingAnnotationsAndForms {
    @Test
    fun `Any parameter annotation`() = test("""
      from typing import Any

      def f(expr: Any):
      #     └ TYPE Any
          pass
      """.trimIndent())

    @Test
    fun `class parameter annotation`() = test("""
      class Foo:    pass

      def f(expr: Foo):
      #     └ TYPE Foo
          pass
      """.trimIndent())

    @Test
    fun `class return type`() = test("""
      class Foo:    pass

      def f() -> Foo:
          pass

      expr = f()
      #└ TYPE Foo
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-16353"])
    fun `assigned type alias as return type`() = test("""
      from typing import Iterable

      IntIterable = Iterable[int]

      def foo() -> IntIterable:
          pass

      expr = foo()
      #└ TYPE Iterable[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-41847"])
    fun `typing Annotated alias`() = test("""
      from typing import Annotated
      A = Annotated[int, 'Some constraint']
      expr: A
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-41847"])
    fun `typing Annotated inline`() = test("""
      from typing_extensions import Annotated
      expr: Annotated[int, 'Some constraint'] = '5'
      #│                                        ^^^ WARNING Expected type 'int', got 'Literal["5"]' instead
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-41847"])
    fun `typing Annotated alias from another file`() = test("""
      from annotated import A
      expr: A = 'str'
      #│        ^^^^^ WARNING Expected type 'int', got 'Literal["str"]' instead
      #└ TYPE int
      """.trimIndent(),
      "annotated.py" to """
        from typing_extensions import Annotated


        A = Annotated[int, 'Some constraint']
        """.trimIndent())

    @Test
    @TestFor(issues = ["PY-82500"])
    fun `function call cannot be used as type hint`() = test("""
      def func() -> type[str]: ...
      expr: func()
      #│    ^^^^^^ WARNING Invalid type annotation
      #└ TYPE Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-29257"])
    fun `generic alias parameters cannot be overridden`() = test("""
      Alias = list[int]
      expr: Alias[str]
      #│          ^^^ WARNING Type alias is not generic or already specialized
      #└ TYPE list[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-20057"])
    fun `non parametrized typing Type maps to builtin type`() = test("""
      from typing import Type

      def f(x: Type):
          expr = x
      #   └ TYPE type
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-20057"])
    fun `typing Type of Any maps to builtin type`() = test("""
      from typing import Type, Any

      def f(x: Type[Any]):
          expr = x
      #   └ TYPE type
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-20057"])
    fun `illegal typing type format`() = test("""
      from typing import Type, Tuple

      def f(x: Tuple[Type[42], Type[], Type[unresolved]]):
      #                             │       ^^^^^^^^^^ ERROR Unresolved reference 'unresolved'
      #                             └ ERROR Expression expected
          expr = x
      #   └ TYPE tuple[Unknown, Unknown, Unknown]
      """.trimIndent())

    @Test
    fun `illegal annotation targets`() = test("""
      (w, _): Tuple[int, Any]
      #│      │          ^^^ ERROR Unresolved reference 'Any'
      #│      ^^^^^ ERROR Unresolved reference 'Tuple'
      #^^^^ ERROR A variable annotation cannot be combined with tuple unpacking
      ((x)): int
      y: bool = z = undefined()
      #│            ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #^^^^^^^^^^^^^^^^^^^^^^^^ ERROR A variable annotation cannot be used in assignment with multiple targets
      expr = (w, x, y, z)
      #└ TYPE tuple[Unknown, int, Unknown, Unknown]
      """.trimIndent())

    @Test
    fun `local variable annotation`() = test("""
      def f():
          x: int = undefined()
      #            ^^^^^^^^^ ERROR Unresolved reference 'undefined'
          expr = x
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-16412"])
    fun `local variable annotation ahead of time explicit Any`() = test("""
      from typing import Any

      def func(x):
          var: Any
          var = x
          expr = var
      #   └ TYPE Any
      """.trimIndent())

    @Test
    fun `local variable annotation ahead of time for target`() = test("""
      x: int
      for x in foo():
      #        ^^^ ERROR Unresolved reference 'foo'
          expr = x
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-21864"])
    fun `local variable annotation ahead of time most recent hint considered`() = test("""
      x: int
      x = foo()
      #   ^^^ ERROR Unresolved reference 'foo'
      x: str
      x = baz()
      #│  ^^^ ERROR Unresolved reference 'baz'
      #\ WARNING Redeclared 'x' defined above without usage
      expr = x
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-21864"])
    fun `local variable annotation ahead of time unpacking target`() = test("""
      x: int
      x, y = foo()
      #      ^^^ ERROR Unresolved reference 'foo'
      expr = x
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-21864"])
    fun `local variable annotation ahead of time with target`() = test("""
      x: int
      with foo() as x:
      #    ^^^ ERROR Unresolved reference 'foo'
          expr = x
      #   └ TYPE int
      """.trimIndent())

    @Test
    fun `unresolved return type not overridden by ancestor annotation`() = test("""
      class Super:
          def m(self) -> int:
              ...
      class Sub(Super):
          def m(self) -> Unresolved:
      #                  ^^^^^^^^^^ ERROR Unresolved reference 'Unresolved'
              ...
      expr = Sub().m()
      #└ TYPE Unknown
      """.trimIndent())
  }

  @Nested
  inner class GenericInferenceAndUnionsOverReceivers {
    @Test
    @TestFor(issues = ["PY-82486"])
    fun `bogus ancestor type var scope owner inference`() = test("""
      from typing import Generic, TypeVar

      T = TypeVar("T")

      class Box(Generic[T]): ...
      class Box2(Box[T]): ...

      def unbox(x: Box[T]) -> T: ...

      def f(x: Box2[T] | None, y: T):
          b: Box2[str]
          expr = y or unbox(b)
      #   └ TYPE T | str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-91009"])
    fun `generic context manager over union binds enter to each member`() = test("""
      class CM[T]:
          def __enter__(self) -> T: ...
          def __exit__(self, *args): ...

      def foo(cm: CM[int] | CM[str]):
          with cm as x:
              expr = x
      #       └ TYPE int | str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-59548"])
    fun `generic base class specified through alias`() = test("""
      from typing import Generic, TypeVar

      T = TypeVar('T')

      class Super(Generic[T]):
          pass

      Alias = Super

      class Sub(Alias[T]):
          pass

      def f(x: Super[T]) -> T:
          pass

      arg: Sub[int]
      expr = f(arg)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-59548"])
    fun `generic base class specified through alias in imported file`() = test("""
      from typing import TypeVar
      from mod import Sub, Super

      T = TypeVar('T')

      def f(x: Super[T]) -> T:
          pass

      arg: Sub[int]
      expr = f(arg)
      #└ TYPE int
      """.trimIndent(),
      "mod.py" to """
        from typing import Generic, TypeVar

        T = TypeVar('T')

        class Super(Generic[T]):
            pass

        Alias = Super

        class Sub(Alias[T]):
            pass
        """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic class defined in another file with PEP695 syntax`() = test("""
      from a import Stack

      expr = Stack[int]().pop()
      #└ TYPE int
      """.trimIndent(),
      "a.py" to """
        class Stack[T]:
            def pop(self) -> T:
                pass
        """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic function defined in another file with PEP695 syntax`() = test("""
      from a import foo

      expr = foo(42)
      #└ TYPE int
      """.trimIndent(),
      "a.py" to """
        def foo[T](x: T) -> T:
            pass
        """.trimIndent())

    @Test
    fun `generic class type hinted in docstrings`() = test("""
      from typing import Generic, TypeVar

      T = TypeVar('T')

      class User1(Generic[T]):
          def __init__(self, x: T):
              self.x = x

          def get(self) -> T:
              return self.x

      c = User1(10)
      expr = c.get()
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-53522"])
    fun `generic parameterized with generic`() = test("""
      from typing import Generic, TypeVar

      T = TypeVar('T')

      class Box(Generic[T]):
          def get(self) -> T:
              pass

      class ListBox(Box[list[T]]):
          pass

      xs: ListBox[int] = ...
      #                  ^^^ WARNING Expected type 'ListBox[int]', got 'EllipsisType' instead
      expr = xs.get()
      #└ TYPE list[int]
      """.trimIndent())

    @Test
    fun `generic substitution in deep hierarchy`() = test("""
      from typing import Generic, TypeVar

      T1 = TypeVar('T1')
      T2 = TypeVar('T2')

      class Root(Generic[T1, T2]):
          def m(self) -> T2:
              pass

      class Base3(Root[T1, int]):
          pass

      class Base2(Base3[T1]):
          pass

      class Base1(Base2[T1]):
          pass

      class Sub(Base1[T1]):
          pass

      expr = Sub().m()
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24834"])
    fun `generic union member call all members are same class parameterizations`() = test("""
      class Box[T]:
          def get(self) -> T:
              pass

      r: Box[int] | Box[str] = ...
      #                        ^^^ WARNING Expected type 'Box[int] | Box[str]', got 'EllipsisType' instead
      expr = r.get()
      #└ TYPE int | str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24834"])
    fun `generic union member call all members own it`() = test("""
      class Box1[T]:
          def get(self) -> T:
              pass

      class Box2[T]:
          def get(self) -> T:
              pass

      r: Box1[int] | Box2[str] = ...
      #                          ^^^ WARNING Expected type 'Box1[int] | Box2[str]', got 'EllipsisType' instead
      expr = r.get()
      #└ TYPE int | str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24834", "PY-83119"])
    fun `generic union member method call some members do not own it`() = test("""
      class Box[T]:
          def get(self) -> T:
              pass
      r: int | Box[str] = ...
      #                   ^^^ WARNING Expected type 'int | Box[str]', got 'EllipsisType' instead
      expr = r.get()
      #│       ^^^ WEAK-WARNING Member 'int' of 'int | Box[str]' does not have attribute 'get'
      #└ TYPE str | Unknown
      """.trimIndent())

    @Test
    @TestCaseOptions
    fun `weak union type of generic method call receiver`() = test("""
      from typing import Any, Generic, TypeVar

      T = TypeVar("T")

      class Box(Generic[T]):
          def get(self) -> T:
              pass

      class StrBox(Box[str]):
          pass

      receiver: Any | int | StrBox = ...
      expr = receiver.get()
      #│              ^^^ WEAK-WARNING Member 'int' of 'Any | int | StrBox' does not have attribute 'get'
      #└ TYPE str | Unknown
      """.trimIndent())
  }

  @Nested
  inner class TopLevelAnnotationAheadOfTimeAcrossFiles {
    @Test
    @TestFor(issues = ["PY-21864"])
    fun `top level variable annotation ahead of time in another file for target`() = test("""
      from other import x

      expr = x
      #└ TYPE int
      """.trimIndent(),
      "other.py" to """
        x: int
        for x in foo():
            expr = x
        """.trimIndent())

    @Test
    @TestFor(issues = ["PY-21864"])
    fun `top level variable annotation ahead of time in another file unpacking target`() = test("""
      from other import x

      expr = x
      #└ TYPE int
      """.trimIndent(),
      "other.py" to """
        x: int
        x, y = foo()
        expr = x
        """.trimIndent())

    @Test
    @TestFor(issues = ["PY-21864"])
    fun `top level variable annotation ahead of time in another file with target`() = test("""
      from other import x

      expr = x
      #└ TYPE int
      """.trimIndent(),
      "other.py" to """
        x: int
        with foo() as x:
            expr = x
        """.trimIndent())
  }

  @Nested
  inner class MultiFileResolutionAndDunderAll {
    @Test
    fun `numpy resolve rater does not increase rate for non-ndarray right operator`() = test("""
      class D1(object):
          pass
      class D2(object):
          pass
      expr = D1() / D2()
      #└ TYPE D1
      """.trimIndent(),
      "aaa.pyi" to """
        class D1(object):
            def __truediv__(self, other) -> "D1": ...

        class D2(object):
            def __rtruediv__(self, other) -> "D2": ...
        """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35881"])
    fun `resolve to another file class with builtin name field`() = test("""
      from foo import Foo
      foo = Foo(0)
      expr = foo.id
      #└ TYPE int
      """.trimIndent(),
      "foo.py" to """
        class Foo:
            def __init__(self, id: int):
                self.id = id
        """.trimIndent())

    @Test
    @TestFor(issues = ["PY-85200"])
    fun `generic class imported under compound TYPE_CHECKING guard`() = test("""
      from multidict import MultiDictProxy

      def f(b: MultiDictProxy[int]):
          expr = b
      #   └ TYPE MultiDictProxy[int]
      """.trimIndent(),
      // Mirrors `multidict`, which exposes the pure-Python generic implementation to a type checker
      // and the C extension at runtime via `if TYPE_CHECKING or not USE_EXTENSIONS:`. Unless the compound
      // condition is recognized, both branches stay reachable and `MultiDictProxy` resolves to the union of
      // the generic class and the non-generic binary-skeleton class, which breaks the `[int]` parametrization.
      "multidict.py" to """
        from typing import TYPE_CHECKING

        from _compat import USE_EXTENSIONS

        if TYPE_CHECKING or not USE_EXTENSIONS:
            from _multidict_py import MultiDictProxy
        else:
            from _multidict import MultiDictProxy
        """.trimIndent(),
      "_compat.py" to """
        import os

        USE_EXTENSIONS = not bool(os.environ.get("MULTIDICT_NO_EXTENSIONS"))
        """.trimIndent(),
      "_multidict_py.py" to """
        from typing import Generic, TypeVar

        _V = TypeVar("_V")

        class MultiDictProxy(Generic[_V]): ...
        """.trimIndent(),
      "_multidict.py" to """
        class MultiDictProxy: ...
        """.trimIndent())
  }

  @Nested
  inner class InspectionOnlyTypeCheckerCases {
    @Test
    @TestFor(issues = ["PY-85988"])
    fun `cls call result of dataclass classmethod`() = test("""
      from dataclasses import dataclass
      from typing import Self


      @dataclass
      class Foo:
          @classmethod
          def bar(cls) -> Self:
              return cls()
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-50642"])
    fun `TYPE_CHECKING branch redeclaration`() = test("""
      import typing

      if typing.TYPE_CHECKING:
          x: str

      if not typing.TYPE_CHECKING:
          x = 1
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-87575"])
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `iter defined in metaclass`() = test("""
      from collections.abc import Iterator
      from typing import assert_type

      # always has the the highest priority on type
      class MyIterMeta(type):
          def __iter__(self) -> Iterator[int]: ...

      class MyClass(metaclass=MyIterMeta): ...

      class MyRedefinedIter(MyClass):
          def __iter__(self) -> Iterator[bool]: ...

      # str redefines __iter__
      class MyFromStr(str, MyRedefinedIter): ...

      assert_type(iter(MyClass), Iterator[int])
      assert_type(iter(MyRedefinedIter), Iterator[int])
      assert_type(iter(MyFromStr), Iterator[int])
      assert_type(iter(MyRedefinedIter()), Iterator[bool])
      assert_type(iter(MyFromStr()), Iterator[str])
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-76659"])
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `types in loop compute fast inspection`() = test("""
      def is_empty(xx: int, yy: int) -> bool:
          ...

      def drop_grain(data: dict) -> None:
          x, y = 500, 0

          while True:
              if is_empty(x, y + 1):
                  x, y = x, y + 1
              elif is_empty(x - 1, y + 1):
                  x, y = x - 1, y + 1
              elif is_empty(x + 1, y + 1):
                  x, y = x + 1, y + 1
              elif not is_empty(x, y):
                  break
              else:
                  data[(x, y)] = 42
                  break
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80436"])
    fun `EllipsisType assignability inspection`() = test("""
      from types import EllipsisType
      e: EllipsisType
      e = ...
      e = Ellipsis
      #\ WARNING Redeclared 'e' defined above without usage
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-21083"])
    fun `float fromhex inspection`() = test("""
      float.fromhex("0.5")
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-16146"])
    fun `typing List subscription expression inspection`() = test("""
      from typing import List, Any


      def f(x1: List[str],
            x2: List['str'],
            x3: List[Any]) -> None:
          pass
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-64124"])
    fun `expected keyword-only parameter matched with regular parameter`() = test("""
      from typing import Protocol


      class MyProtocol(Protocol):
          def method(self, *, param) -> None:
              pass


      def f(xs: MyProtocol):
          pass


      class MyClass:
          def method(self, param) -> None:
              pass


      f(MyClass())
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-64124"])
    fun `expected positional-only parameter matched with regular parameter`() = test("""
      from typing import Protocol


      class MyProtocol(Protocol):
          def method(self, param, /) -> None:
              pass


      def f(xs: MyProtocol):
          pass


      class MyClass:
          def method(self, param) -> None:
              pass


      f(MyClass())
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-28957"])
    fun `dataclasses replace argument checking`() = test("""
      from dataclasses import dataclass, field, InitVar, replace


      @dataclass
      class A:
          a: int
          b: str = "str"


      replace(A(1))
      replace(A(1), a=1, b="abc")
      replace(A(1), a="str", b=1)
      #             │        ^^^ WARNING Expected type 'str', got 'Literal[1]' instead
      #             ^^^^^^^ WARNING Expected type 'int', got 'Literal["str"]' instead


      @dataclass
      class B:
          a: int
          b: str = field(default="str", init=False)


      replace(B(1))
      replace(B(1), a=1)
      replace(B(1), a="str") # WARNING Expected type 'int', got 'Literal["str"]' instead


      @dataclass
      class C:
          a: int
          b: InitVar[str] = "str"
      #   └ WARNING Attribute 'b' is useless until '__post_init__' is declared


      replace(C(1))
      replace(C(1), a=1, b="str")
      replace(C(1), a="str", b=1)
      #             │        ^^^ WARNING Expected type 'str', got 'Literal[1]' instead
      #             ^^^^^^^ WARNING Expected type 'int', got 'Literal["str"]' instead


      class D:
          pass


      replace(D())
      #       ^^^ WARNING 'dataclasses.replace' method should be called on dataclass instances
      #       ^^^ WARNING Expected type '_DataclassT ≤: DataclassInstance', got 'D' instead
      replace(D(), a=1, b=2)
      #       ^^^ WARNING 'dataclasses.replace' method should be called on dataclass instances
      #       ^^^ WARNING Expected type '_DataclassT ≤: DataclassInstance', got 'D' instead
      """.trimIndent())
  }

  @Nested
  inner class NewAnyUnknownRendering {
    @Test
    @TestCaseOptions(enablePyAnyType = false)
    @TestFor(issues = ["PY-81651"])
    fun `eq with Any without new any`() = test("""
      from typing import Any

      class A:
          def __eq__(self, other) -> Any:
            return "hello :)"

      expr = A() == 1
      #└ TYPE UnsafeUnion[Any, bool]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-81651"])
    fun `eq with`() = test("""
      from typing import Any

      class A:
          def __eq__(self, other) -> Any:
            return "hello :)"

      expr = A() == 1
      # └ TYPE Any
      """.trimIndent())

    @Test
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `unknown reference`() = test("""
      expr = x
      #│     └ ERROR Unresolved reference 'x'
      #└ TYPE Unknown
      """.trimIndent())

    @Test
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `unknown list`() = test("""
      expr = [x]
      #│      └ ERROR Unresolved reference 'x'
      #└ TYPE list[Unknown]
      """.trimIndent())

    @Test
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `unknown generator`() = test("""
      def f():
        a = yield x
      #           └ ERROR Unresolved reference 'x'
        return a

      expr = f()
      #└ TYPE Generator[Unknown, Unknown, Unknown]
      """.trimIndent())

    @Test
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `generic identity over unknown list`() = test("""
      def f[T](t: T) -> T: ...

      expr = f([x])
      #│        └ ERROR Unresolved reference 'x'
      #└ TYPE list[Unknown]
      """.trimIndent())

    @Test
    fun `generic element extraction over unknown list`() = test("""
      def f[T](t: list[T]) -> T: ...

      expr = f([x])
      #│        └ ERROR Unresolved reference 'x'
      #└ TYPE Unknown
      """.trimIndent())

    @Test
    fun `plain Any`() = test("""
      from typing import Any

      expr: Any
      #└ TYPE Any
      """.trimIndent())

    @Test
    fun `simple unknown`() = test("""
      expr = asdf
      #│     ^^^^ ERROR Unresolved reference 'asdf'
      #└ TYPE Unknown
      """.trimIndent())
  }

  @Test
  @TestFor(issues = ["PY-5873"])
  fun `method raising NotImplementedError has no inferred return type`() = test("""
    def f1(x: int):
        pass

    class C:
        def f(self):
            raise NotImplementedError()

    x = C()
    f1(x.f())
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-14222"])
  fun `recursive self attribute assignment does not break inference`() = test("""
    class C:
        def f(self, x):
            self.foo = x
            self.foo = {'foo': self.foo}
            return self.foo['foo'] + 10
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-78964"])
  fun `return type is checked through try-finally`() = test("""
    def test() -> bool:
        try:
            pass
        finally:
            pass

        return True
    """.trimIndent())

  @Test
  @TestCaseOptions(assertRecursionPrevention = false)
  fun `NoneType and type of None`() = test("""
    from types import NoneType

    x: NoneType = None
    y: type[NoneType] = type(None)
    z: type[None] = NoneType
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-27551"])
  fun `dunder init annotated with NoReturn`() = test("""
    from typing import NoReturn

    class Test:
        def __init__(self) -> NoReturn:
            raise Exception()
    """.trimIndent())

  @Test
  fun `dunder init annotated as non none`() = test("""
    class A:
        def __init__(self) -> int: # WARNING __init__ should return None
            pass


    class B:
        def __init__(self, foo: str) -> int: # WARNING __init__ should return None
            pass
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-7179"])
  fun `identity decorated function keeps its type in operations`() = test("""
    def decorator(f):
        return f

    @decorator
    def foo():
        return 'foo'

    print(foo + 3) # WARNING Expected type 'int', got '() -> Literal["foo"]' instead
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-29704"])
  fun `passing abstract method result is not reported`() = test("""
    import abc

    class Foo(metaclass=abc.ABCMeta):

        @abc.abstractmethod
        def get_int(self) -> int:
            pass

        def foo(self, i: int) -> None:
            print(i)

        def bar(self):
            self.foo(self.get_int())
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-16066"])
  fun `function return type list of list mismatch`() = test("""
    def a(x: list[int]) -> list[str]:
        return [x] # WARNING Expected type 'list[str]', got 'list[list[int]]' instead
    """.trimIndent())
}

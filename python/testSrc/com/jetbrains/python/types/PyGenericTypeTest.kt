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
 * Type inference tests for generics and type parameters: [typing.Generic](https://docs.python.org/3/library/typing.html#typing.Generic),
 * [TypeVar](https://docs.python.org/3/library/typing.html#typing.TypeVar) (bounds/constraints/defaults),
 * PEP 695 syntax (`class C[T]`, `def f[T]`, `type X[T] = ...`), `TypeVarTuple`, generic class/function/method
 * inference and type-parameter substitution/unification.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyGenericTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class DocstringBasedGenerics {
    @Test
    fun `generic concrete from docstring`() = test("""
      def f(x):
          '''
          :type x: T
          :rtype: T
          '''
          return x
      
      expr = f(1)
      # └ TYPE int
      """.trimIndent())

    @Test
    fun `generic concrete mismatch from docstring`() = test("""
      def f(x, y):
          '''
          :type x: T
          :rtype: T
          '''
          return x
      
      expr = f(1)
      #│        └ WARNING Parameter 'y' unfilled
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `upper bound generic from docstring`() = test("""
      def foo(x):
          '''
          :type x: T <= int or str
          :rtype: T
          '''
      def bar(x):
          expr = foo(x)
      #   └ TYPE int | str
      """.trimIndent())

    @Test
    fun `function type as unification argument`() = test("""
      def map2(f, xs):
          '''
          :type f: (T) -> V | None
          :type xs: collections.Iterable[T] | str | unicode
          :rtype: list[V] | str | unicode
          '''
          pass

      expr = map2(lambda x: 10, ['1', '2', '3'])
      #└ TYPE list[int] | str FIXME Union[List[int], str, unicode]
      """.trimIndent())

    @Test
    fun `function type as unification argument with subscription`() = test("""
      def map2(f, xs):
          '''
          :type f: (T) -> V | None
          :type xs: collections.Iterable[T] | str | unicode
          :rtype: list[V] | str | unicode
          '''
          pass
      
      expr = map2(lambda x: 10, ['1', '2', '3'])[0]
      # └ TYPE int | str FIXME Union[int, str, unicode]
      """.trimIndent())

    @Test
    fun `function type as unification result`() = test("""
      def f(x):
          '''
          :type x: T
          :rtype: () -> T
          '''
          pass
      
      g = f(10)
      expr = g()
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `heterogeneous tuple substitution from docstring`() = test("""
      def foo(i):
          '''
          :type i: T
          :rtype: tuple[T, T]
          '''
          pass
      expr = foo(5)
      #└ TYPE tuple[int, int]
      """.trimIndent())

    @Test
    fun `unknown tuple substitution from docstring`() = test("""
      def foo(i):
          '''
          :type i: T
          :rtype: tuple
          '''
          pass
      expr = foo(5)
      #└ TYPE tuple
      """.trimIndent())

    @Test
    fun `constructor unification from docstring`() = test("""
      class C(object):
          def __init__(self, x):
              '''
              :type x: T
              :rtype: C[T]
              '''
              pass
      
      expr = C(10)
      #└ TYPE C[int]
      """.trimIndent())

    @Test
    fun `generic class method unification from docstring`() = test("""
      class C(object):
          def __init__(self, x):
              '''
              :type x: T
              :rtype: C[T]
              '''
              pass
          def foo(self):
              '''
              :rtype: T
              '''
              pass
      
      expr = C(10).foo()
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `generic functions use same type parameter from docstring`() = test("""
      def id(x):
          '''
          :type x: T
          :rtype: T
          '''
          return x
      
      def f3(x):
          '''
          :type x: T
          '''
          return id(x)
      expr = f3(42)
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `generic field from docstring`() = test("""
      class D(object):
          def __init__(self, foo):
              '''
              :type foo: T
              :rtype: D[T]
              '''
              self.foo = foo
      
      
      def g():
          '''
          :rtype: D[str]
          '''
          return D('test')
      
      
      y = g()
      expr = y.foo
      #└ TYPE str
      """.trimIndent())

    @Test
    fun `type var substitution in positional args from docstring`() = test("""
      def foo(*args):
          '''
          :type args: T
          :rtype: T
          '''
          pass
      expr = foo(1)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-19723"])
    fun `type var substitution in heterogeneous positional args from docstring`() = test("""
      def foo(*args):
          '''
          :type args: T
          :rtype: T
          '''
          pass
      expr = foo(1, "2")
      # └ TYPE int | str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-19723"])
    fun `type var substitution in keyword args from docstring`() = test("""
      def foo(**kwargs):
          '''
          :type kwargs: T
          :rtype: T
          '''
          pass
      expr = foo(a=1)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-19723"])
    fun `type var substitution in heterogeneous keyword args from docstring`() = test("""
      def foo(**kwargs):
          '''
          :type kwargs: T
          :rtype: T
          '''
          pass
      expr = foo(a=1, b="2")
      #└ TYPE int | str
      """.trimIndent())

    @Test
    fun `non-trivial generic argument type in docstring`() = test("""
      def f1(xs):
          '''
          :type xs: collections.Iterable of T
          '''
          return iter(xs)
      
      expr = f1([1, 2, 3])
      #└ TYPE UnsafeUnion[SupportsNext[Any], Iterator[Unknown]] FIXME Iterator[int]
      """.trimIndent())

    @Test
    fun `generic class type var from docstrings`() = test("""
      class User1(object):
          def __init__(self, x):
              '''
              :type x: T
              :rtype: User1 of T
              '''
              self.x = x
      
          def get(self):
              '''
              :rtype: T
              '''
              return self.x
      
      c = User1(10)
      expr = c.get()
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON35, assertRecursionPrevention = false)
    fun `homogeneous tuple substitution`() = test("""
      from typing import TypeVar, Tuple
      T = TypeVar('T')
      def foo(i: T) -> Tuple[T, ...]:
          pass
      expr = foo(5)
      #└ TYPE Tuple[int, ...]
      """.trimIndent())
  }

  @Nested
  inner class TypeVarReferenceAndTarget {
    @Test
    fun `TypeVar call target type`() = test("""
      from typing import TypeVar
      expr = TypeVar('T')
      #│             ^^^ WARNING The argument to 'TypeVar()' must be a string equal to the variable name to which it is assigned
      #└ TYPE TypeVar
      """.trimIndent())

    @Test
    fun `type parameter type is typing TypeVar`() = test("""
      def foo[T]():
         expr = T
      #  └ TYPE TypeVar
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `type parameter type is typing TypeVarTuple`() = test("""
      def foo[*Ts]():
         expr = Ts
      #  └ TYPE TypeVarTuple
      """.trimIndent())

    @Test
    fun `type parameter rebind to local`() = test("""
      def outer[T]() -> None:
          def inner() -> None:
              expr = T
      #       └ TYPE int
      
          T = -1
      """.trimIndent())
  }

  @Nested
  inner class TypingGenericAndTypeVarLegacySyntax {
    @Test
    fun `TypeVar used as parameter annotation`() = test("""
      from typing import TypeVar

      T = TypeVar('A')
      #           ^^^ WARNING The argument to 'TypeVar()' must be a string equal to the variable name to which it is assigned

      def f(expr: T):
      #      └ TYPE A
          pass
      """.trimIndent())

    @Test
    fun `bounded TypeVar used as parameter annotation`() = test("""
      from typing import TypeVar
      
      T = TypeVar('T', int, str)
      
      def f(expr: T):
      #      └ TYPE T
          pass
      """.trimIndent())

    @Test
    fun `parameterized class instance`() = test("""
      from typing import Generic, TypeVar
      
      T = TypeVar('T')
      
      class C(Generic[T]):
          def __init__(self, x: T):
              pass
      
      expr = C(10)
      # └ TYPE C[int]
      """.trimIndent())

    @Test
    fun `parameterized class with constructor returning None`() = test("""
      from typing import Generic, TypeVar
      
      T = TypeVar('T')
      
      class C(Generic[T]):
          def __init__(self, x: T) -> None:
              pass
      
      expr = C(10)
      #└ TYPE C[int]
      """.trimIndent())

    @Test
    fun `parameterized class method`() = test("""
      from typing import Generic, TypeVar
      
      T = TypeVar('T')
      
      class C(Generic[T]):
          def __init__(self, x: T):
              pass
          def foo(self) -> T:
              pass
      
      expr = C(10).foo()
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `parameterized class inheritance`() = test("""
      from typing import Generic, TypeVar
      
      T = TypeVar('T')
      
      class B(Generic[T]):
          def foo(self) -> T:
              pass
      class C(B[T]):
          def __init__(self, x: T):
              pass
      
      expr = C(10).foo()
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `AnyStr unification`() = test("""
      from typing import AnyStr
      
      def foo(x: AnyStr) -> AnyStr:
          pass
      
      expr = foo(b'bar')
      #└ TYPE bytes
      """.trimIndent())

    @Test
    fun `AnyStr for unknown`() = test("""
      from typing import AnyStr
      
      def foo(x: AnyStr) -> AnyStr:
          pass
      
      def bar(x):
          expr = foo(x)
      #   └ TYPE AnyStr
      """.trimIndent())

    @Test
    fun `generic field with covariant TypeVar`() = test("""
      from typing import TypeVar, Generic
      
      T = TypeVar('T', covariant=True)
      
      class C(Generic[T]):
          def __init__(self, foo: T):
              self.foo = foo
      
      def f() -> C[str]:
          return C('test')
      
      x = f()
      expr = x.foo
      #└ TYPE str
      """.trimIndent())

    @Test
    fun `generic field accessed on explicit Any and bare generic`() = test("""
      from typing import Any
      
      class A[T]:
          v: T
      
      def f(a1: A[Any], a2: A):
          expr = a1.v, a2.v
      #   └ TYPE tuple[Any, Unknown]
      """.trimIndent())

    @Test
    fun `generic inherited specific and generic parameters`() = test("""
      from typing import TypeVar, Generic, Tuple, Iterator, Iterable
      
      T = TypeVar('T')
      
      class B(Generic[T]):
          pass
      
      class C(B[Tuple[int, T]], Generic[T]):
          def __init__(self, x: T) -> None:
              pass
      
      expr = C(3.14)
      # └ TYPE C[float | int]
      """.trimIndent())

    @Test
    fun `generic renamed parameter`() = test("""
      from typing import TypeVar, Generic
      
      T = TypeVar('T')
      V = TypeVar('V')
      
      class B(Generic[V]):
          def get(self) -> V:
              pass
      
      class C(B[T]):
          def __init__(self, x: T) -> None:
              pass
      
      expr = C(0).get()
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27627"])
    fun `explicitly parametrized generic class instance`() = test("""
      from typing import TypeVar, Generic, List
      
      T = TypeVar('T')
      class Node(Generic[T]):
          def __init__(self, children : List[T]):
              self.children = children
      expr = Node[int]()
      # │              └ WARNING Parameter 'children' unfilled
      # └ TYPE Node[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27627"])
    fun `multi-type explicitly parametrized generic class instance`() = test("""
      from typing import TypeVar, Generic
      
      T = TypeVar('T')
      V = TypeVar('V')
      Z = TypeVar('Z')
      
      class FirstType(Generic[T]): pass
      class SecondType(Generic[V]): pass
      class ThirdType(Generic[Z]): pass
      
      class Clazz(FirstType[T], SecondType[V], ThirdType[Z]):
          first: T
          second: V
          third: Z
      
          def __init__(self):
              pass
      
      node = Clazz[str, int, float]()
      expr = node.third
      #└ TYPE float | int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27627"])
    fun `explicitly parametrized generic class instance typization priority`() = test("""
      from typing import TypeVar, Generic, List

      T = TypeVar('T')
      class Node(Generic[T]):
          def __init__(self, children : List[T]):
              self.children = children
      expr = Node[str]([1,2,3])
      #│               ^^^^^^^ WARNING Expected type 'list[str]', got 'list[Literal[1, 2, 3]]' instead
      #└ TYPE Node[str]
      """.trimIndent())

    @Test
    fun `generic user function with many params and nested call`() = test("""
      from typing import TypeVar
      
      T = TypeVar('T')
      U = TypeVar('U')
      V = TypeVar('V')
      
      def myid(x: T) -> T:
          pass
      
      def f(x: T, y: U, z: V):
          return myid(x), myid(y), myid(z)
      
      expr = f(True, 1, 'foo')
      #└ TYPE tuple[bool, int, str]
      """.trimIndent())
  }

  @Nested
  inner class ClassObjectTypesWithTypeVars {
    @Test
    @TestFor(issues = ["PY-20057"])
    fun `class object type of parameter`() = test("""
      from typing import Type
      
      class MyClass:
          pass
      
      def f(x: Type[MyClass]): 
          expr = x
      #   └ TYPE type[MyClass]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-20057"])
    fun `constrained class object type of parameter`() = test("""
      from typing import Type, TypeVar
      
      T = TypeVar('T', bound=int)
      
      def f(x: Type[T]):
          expr = x
      #   └ TYPE type[T]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-20057"])
    fun `function creates instance from type`() = test("""
      from typing import Type, TypeVar
      
      T = TypeVar('T')
      
      def f(x: Type[T]) -> T:
          return x()
      
      expr = f(int)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-20057"])
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `function returns type of instance`() = test("""
      from typing import Type, TypeVar
      
      T = TypeVar('T')
      
      def f(x: T) -> Type[T]:
          return type(x)
         
      expr = f(42)
      #└ TYPE type[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-23053"])
    fun `unbound generic matches class object types`() = test("""
      from typing import Generic, TypeVar
      
      T = TypeVar('T')
      
      class Holder(Generic[T]):
          def __init__(self, value: T):
              self._value = value
      
          def get(self) -> T:
              return self._value
      
      expr = Holder(str).get()
      #└ TYPE type[str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24260"])
    fun `generic class parameter taken from generic class object`() = test("""
      from typing import TypeVar, Generic, Type
      
      T = TypeVar("T")
      
      class MyClass(Generic[T]):
          def __init__(self, type: Type[T]):
              pass
      
      def f(x: Type[T]):
          expr = MyClass(x)
      #   └ TYPE MyClass[T]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-22919"])
    fun `particular type against TypeVar bounded with builtin type`() = test("""
      from typing import TypeVar, Type
      
      T = TypeVar("T", bound=type)
      
      def foo(t: T) -> T:
          pass
      
      class MyClass:
          pass
      
      expr = foo(MyClass)
      #└ TYPE type[MyClass]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-60614"])
    fun `parameterized TypeAlias for type of TypeVar`() = test("""
      from typing import TypeVar
      T = TypeVar('T')
      TypeAlias = type[T]
      expr: TypeAlias[int]
      #└ TYPE type[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-60614"])
    fun `call return type inferred via parameterized TypeAlias for type of TypeVar`() = test("""
      from typing import TypeVar
      T = TypeVar('T')
      TypeAlias = type[T]
      def f(x: TypeAlias[T]) -> T: ...
      expr = f(int)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-60614"])
    fun `parameterized TypeAlias for type of TypeVar with PEP695 syntax`() = test("""
      type TypeAlias[T] = type[T]
      expr: TypeAlias[int]
      #└ TYPE type[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-90345"])
    fun `call inference via union TypeAlias with class-object arm`() = test("""
      from typing import Generic, TypeVar
      T = TypeVar('T')
      class Role(Generic[T]): ...
      Alias = Role[T] | type[T]
      def f(x: Alias[T]) -> T: ...
      expr = f(int)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-90345"])
    fun `call inference via union TypeAlias with class-object arm first`() = test("""
      from typing import Generic, TypeVar
      T = TypeVar('T')
      class Role(Generic[T]): ...
      Alias = type[T] | Role[T]
      def f(x: Alias[T]) -> T: ...
      expr = f(int)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-90345"])
    fun `call inference via three-arm union TypeAlias with class-object`() = test("""
      from typing import Generic, TypeVar
      T = TypeVar('T')
      class A(Generic[T]): ...
      class B(Generic[T]): ...
      Alias = A[T] | B[T] | type[T]
      def f(x: Alias[T]) -> T: ...
      expr = f(int)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-90345"])
    fun `parameterized union TypeAlias with class-object arm`() = test("""
      from typing import Generic, TypeVar
      T = TypeVar('T')
      class Role(Generic[T]): ...
      Alias = Role[T] | type[T]
      expr: Alias[int]
      #└ TYPE Role[int] | type[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-90345"])
    fun `parameterized union TypeAlias with class-object arm PEP695`() = test("""
      class Role[T]: ...
      type Alias[T] = Role[T] | type[T]
      expr: Alias[int]
      #└ TYPE Role[int] | type[int]
      """.trimIndent())
  }

  @Nested
  inner class GenericInferenceAndUnification {
    @Test
    fun `generic method call unification`() = test("""
      from typing import Generic, TypeVar
      T = TypeVar("T")
      
      class Box(Generic[T]):
          def __init__(self, value: T) -> None:
              self.value = value
          def get(self) -> T:
              return self.value
      
      box = Box(42)
      expr = box.get()
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `single TypeVar specified on inheritance`() = test("""
      from typing import Generic, TypeVar
      
      T = TypeVar("T")
      
      class Box(Generic[T]):
          pass
      
      class StrBox(Box[str]):
          pass
      
      def extract(b: Box[T]) -> T:
          pass
      
      box = StrBox()
      expr = extract(box)
      #└ TYPE str
      """.trimIndent())

    @Test
    fun `partial TypeVar specialization on inheritance inherited`() = test("""
      from typing import Generic, TypeVar
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      
      class Pair(Generic[T1, T2]):
          pass
      
      class StrFirstPair(Pair[str, T2]):
          def __init__(self, second: T2):
              pass
      
      def first(pair: Pair[T1, T2]) -> T1:
          pass
      
      expr = first(StrFirstPair(42))
      #└ TYPE str
      """.trimIndent())

    @Test
    fun `partial TypeVar specialization on inheritance instantiated`() = test("""
      from typing import Generic, TypeVar
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      
      class Pair(Generic[T1, T2]):
          pass
      
      class StrFirstPair(Pair[str, T2]):
          def __init__(self, second: T2):
              pass
      
      def second(pair: Pair[T1, T2]) -> T2:
          pass
      
      expr = second(StrFirstPair(42))
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `TypeVars not specialized on inheritance distinct TypeVars`() = test("""
      from typing import Generic, TypeVar
      
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      T3 = TypeVar('T3')
      T4 = TypeVar('T4')
      T5 = TypeVar('T5')
      T6 = TypeVar('T6')
      
      class Pair(Generic[T1, T2]):
          pass
      
      class PairExt(Pair[T3, T4]):
          def __init__(self, first: T3, second: T4):
              pass
      
      def to_tuple(pair: Pair[T5, T6]) -> tuple[T5, T6]:
          pass
      
      expr = to_tuple(PairExt(42, 'foo'))
      #└ TYPE tuple[int, str]
      """.trimIndent())

    @Test
    fun `TypeVars not specialized on inheritance reused TypeVars`() = test("""
      from typing import Generic, TypeVar
      
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      
      class Pair(Generic[T1, T2]):
          pass
      
      class PairExt(Pair[T1, T2]):
          def __init__(self, first: T1, second: T2):
              pass
      
      def to_tuple(pair: Pair[T1, T2]) -> tuple[T1, T2]:
          pass
      
      expr = to_tuple(PairExt(42, 'foo'))
      #└ TYPE tuple[int, str]
      """.trimIndent())

    @Test
    fun `TypeVar specialized on inheritance extra TypeVar added`() = test("""
      from typing import Generic, TypeVar
      
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      T3 = TypeVar('T3')
      
      class Box(Generic[T1]):
          pass
      
      class StrBoxWithExtra(Box[str], Generic[T2]):
          def __init__(self, extra: T2):
              self.extra = extra
      
      def func(b: Box[T3]) -> T3:
          pass
      
      box = StrBoxWithExtra(42)
      expr = func(box)
      #└ TYPE str
      """.trimIndent())

    @Test
    fun `generic class specializes inherited parameter and adds new one`() = test("""
      from typing import Generic, TypeVar
      
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      
      class Box(Generic[T1]):
          pass
      
      class StrBoxWithExtra(Box[str], Generic[T2]):
          def __init__(self, extra: T2):
              self.extra = extra
      
      expr = StrBoxWithExtra(42)
      #└ TYPE StrBoxWithExtra[int]
      """.trimIndent())

    @Test
    fun `swapping type parameters in constructor`() = test("""
      from typing import Generic, TypeVar
      
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      
      
      class Pair(Generic[T1, T2]):
          def __init__(self, pair: 'Pair[T2, T1]'):
              pass
      
      
      int_then_str: Pair[int, str] = ...()
      #                              ^^^^^ WARNING 'EllipsisType' object is not callable
      expr = Pair(int_then_str)
      #└ TYPE Pair[str, int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-50542"])
    fun `reused TypeVars and inheritance do not cause recursive substitution`() = test("""
      from typing import Generic, TypeVar
      
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      
      class Super(Generic[T1]):
          pass
      
      class Sub(Super[T2]):
          def __init__(self, xs: list[T2]):
              pass
      
      def func(xs: list[T1]):
          expr = Sub(xs)
      #   └ TYPE Sub[T1]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-50542"])
    fun `reused TypeVars in opposite order do not cause recursive substitution`() = test("""
      from typing import TypeVar
      
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      
      def f(x: T1, y: T2) -> T2:
          pass
      
      def g(x: T2, y: T1):
          return f(x, y)
      
      expr = g(42, 'foo')
      #└ TYPE str
      """.trimIndent())

    @Test
    fun `generic kwargs`() = test("""
      from typing import Any, Dict, TypeVar
      
      T = TypeVar('T')
      
      def generic_kwargs(**kwargs: T) -> Dict[str, T]:
          pass
      
      expr = generic_kwargs(a=1, b='foo')
      #└ TYPE dict[str, int | str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27783"])
    fun `applying super substitution to generic class`() = test("""
      from typing import TypeVar, Generic, Dict, List

      T = TypeVar('T')

      class A(Generic[T]):
          pass

      class B(A[List[T]], Generic[T]):
          def __init__(self) -> None:
              self.value_set: Dict[T, int] = {}

          def foo(self) -> None:
              expr = self.value_set
      #       └ TYPE dict[T, int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27783"])
    fun `applying super substitution to bounded generic class`() = test("""
      from typing import TypeVar, Generic, Dict, List

      T = TypeVar('T', bound=str)

      class A(Generic[T]):
          pass

      class B(A[List[T]], Generic[T]):
      #         ^^^^^^^ WARNING Expected type 'T ≤: str', got 'list[T]' instead
          def __init__(self) -> None:
              self.value_set: Dict[T, int] = {}

          def foo(self) -> None:
              expr = self.value_set
      #       └ TYPE dict[T, int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-36008"])
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON36, assertRecursionPrevention = false)
    fun `unresolved generic replacement`() = test("""
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

    @Test
    fun `class with own init inherits class with generic call`() = test("""
      from typing import Any, Generic, TypeVar
      
      T = TypeVar("T")
      
      class Base(Generic[T]):
          def __call__(self, p: Any) -> T:
              pass
      
      class Derived(Base):
          def __init__(self):
              pass
      
      expr = Derived()
      #└ TYPE Derived
      """.trimIndent())

    @Test
    fun `handle generic return type`() = test("""
      from typing import List
      
      def create_list_of_type[T](item: T, count: int) -> List[T]:
          return [item] * count
      
      expr = create_list_of_type("foo", 3)
      #└ TYPE list[str]
      """.trimIndent())

    @Test
    fun `handle generic with aliases return type`() = test("""
      from typing import Dict, TypeAlias, TypeVar
      
      V = TypeVar("V")
      
      StringDict = Dict[str, V]
      
      def create_dict_of_type[T](item: T,) -> StringDict[T]:
          return {"foo": item}
      
      
      dict = create_dict_of_type(23)
      
      expr = dict.get("foo")
      #└ TYPE int | None
      """.trimIndent())

    @Test
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `TypeVar constraints with legacy syntax`() = test("""
      from typing import TypeVar
      
      AnyStr = TypeVar('AnyStr', str, bytes)
      
      def concat(x: AnyStr, y: AnyStr) -> AnyStr:
          return x + y
      
      class MyStr(str): ...
      
      s1 = concat(MyStr('apple'), MyStr('pie'))
      s2 = concat(MyStr('apple'), 'pie')
      expr = (s1, s2)
      #└ TYPE tuple[str, str]
      """.trimIndent())

    @Test
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `TypeVar constraints with PEP695 syntax`() = test("""
      def concat[AnyStr: (str, bytes)](x: AnyStr, y: AnyStr) -> AnyStr:
          return x + y
      
      class MyStr(str): ...
      
      s1 = concat(MyStr('apple'), MyStr('pie'))
      s2 = concat(MyStr('apple'), 'pie')
      expr = (s1, s2)
      #└ TYPE tuple[str, str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-89265"])
    fun `explicit None as generic argument`() = test("""
      class A[T]:
          x: T
      
      def f(a: A[None]):
          expr = a.x
      #   └ TYPE None
      """.trimIndent())

    @Test
    fun `generic function rendered signature`() = test("""
      def f[T: int = str, *Ts = *tuple[int], **P = [str]](t: T) -> T: ...
      #              ^^^ WARNING Default type of TypeVar is not a subtype of the bound

      expr = f
      #└ TYPE [T: int = str, *Ts = *tuple[int], **P = [str]](t: T) -> T
      """.trimIndent())
  }

  @Nested
  inner class TypeVarTupleVariadicGenerics {
    @Test
    fun `type of args parameter annotated with TypeVarTuple`() = test("""
      from typing import TypeVarTuple
      
      Ts = TypeVarTuple("Ts")
      
      def f(*args: *Ts):
          expr = args
      #   └ TYPE tuple[*Ts]
      """.trimIndent())

    @Test
    fun `type of args parameter annotated with bound unpacked tuple`() = test("""
      def f(*args: *tuple[int, str]):
          expr = args
      #   └ TYPE tuple[int, str]
      """.trimIndent())

    @Test
    fun `type of args parameter annotated with unbound unpacked tuple`() = test("""
      def f(*args: *tuple[int, ...]):
          expr = args
      #   └ TYPE tuple[int, ...]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic method call unification`() = test("""
      from typing import Generic, TypeVarTuple, Tuple
      
      Ts = TypeVarTuple("Ts")
      
      class Box(Generic[*Ts]):
        def __init__(self, value: Tuple[*Ts]) -> None:
            self.value = value
        def get(self):
            return self.value
      
      box = Box((42, 'a', 3.3))
      expr = box.get()
      #└ TYPE tuple[int, str, float | int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `single TypeVarTuple specified on inheritance`() = test("""
      from typing import Generic, TypeVarTuple, Tuple
      
      Ts = TypeVarTuple("Ts")
      
      class Box(Generic[*Ts]):
          pass
      
      class StrBox(Box[str, int]):
          pass
      
      def extract(b: Box[*Ts]) -> Tuple[*Ts]:
          pass
      
      box = StrBox()
      expr = extract(box)
      #└ TYPE tuple[str, int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `TypeVarTuple specialized on inheritance extra TypeVar added`() = test("""
      from typing import Generic, TypeVarTuple, Tuple
      
      Ts1 = TypeVarTuple('Ts1')
      Ts2 = TypeVarTuple('Ts2')
      Ts3 = TypeVarTuple('Ts3')
      
      class Box(Generic[*Ts1]):
          pass
      
      class StrBoxWithExtra(Box[str, int], Generic[*Ts2]):
          def __init__(self, extra: Tuple[*Ts2]):
              self.extra = extra
      
      def func(b: Box[*Ts3]) -> Tuple[*Ts3]:
          pass
      
      box = StrBoxWithExtra((42, 'a', 3.3))
      expr = func(box)
      #└ TYPE tuple[str, int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic class specializes inherited parameter and adds new one`() = test("""
      from typing import Generic, TypeVarTuple, Tuple
      
      Ts1 = TypeVarTuple('Ts1')
      Ts2 = TypeVarTuple('Ts2')
      
      class Box(Generic[*Ts1]):
          pass
      
      class StrBoxWithExtra(Box[str], Generic[*Ts2]):
          def __init__(self, extra: Tuple[*Ts2]):
              self.extra = extra
      
      expr = StrBoxWithExtra((42, 'a', 3.3))
      #└ TYPE StrBoxWithExtra[int, str, float | int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-70528"])
    fun `TypeVarTuple and Unpack from typing_extensions`() = test("""
      from typing_extensions import TypeVarTuple, Unpack
      
      Ts = TypeVarTuple("Ts")
      
      def f(*args: Unpack[Ts]) -> tuple[Unpack[Ts]]:
          ...
      
      expr = f(42, "foo")
      #└ TYPE tuple[int, str]
      """.trimIndent())
  }

  @Test
  @TestFor(issues = ["PY-85336"])
  fun `apply expected generic type to right hand side`() = test("""
      class A[T]: ...
      class B[T](A[T]): ...
      
      a: A[int] = B()
      #             └ TYPE B[Unknown] FIXME B[int]
      """.trimIndent())

  @Test
  @TestFor(issues = ["PY-88690"])
  fun `no false positive assigning partially specialized generic subclass`() = test("""
      class A[X, Y]:
          def __init__(self, x: X, y: Y): ...

      class B[T](A[int, T]): ...

      a: B[int] = B(1, 2)
      #                 └ TYPE B[int]
      """.trimIndent())

  @Nested
  inner class Pep695Syntax {
    @Test
    @TestFor(issues = ["PY-61883"])
    fun `simple generic class with PEP695 type parameter syntax`() = test("""
      class MyStack[T]:
          def pop(self) -> T:
              pass
      
      stack = MyStack[str]()
      expr = stack.pop()
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `simple generic function with PEP695 syntax`() = test("""
      def foo[T](x: T) -> T:
        return x
      
      expr = foo(1)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic class parameterized with type of constructor argument with PEP695 syntax`() = test("""
      class C[T]:
          def __init__(self, x: T):
              pass
      
      expr = C(10)
      #└ TYPE C[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic base with PEP695 syntax class specified through alias`() = test("""
      class Super[T]:
          pass
      
      Alias = Super
      
      class Sub[T](Alias[T]):
          pass
      
      def f[T](x: Super[T]) -> T:
          pass
      
      arg: Sub[int]
      expr = f(arg)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `explicitly parametrized generic class instance with PEP695 syntax`() = test("""
      from typing import List
      
      class Node[T]:
          def __init__(self, children: List[T]):
              self.children = children


      expr = Node[int]()
      #│               └ WARNING Parameter 'children' unfilled
      #└ TYPE Node[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `parameterized with PEP695 syntax class inheritance`() = test("""
      class B[T]:
          def foo(self) -> T:
              pass
      
      class C[T](B[T]):
          def __init__(self, x: T):
              pass
      
      expr = C(10).foo()
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic field of class parameterized with new PEP695 syntax`() = test("""
      class C[T]:
          def __init__(self, foo: T):
              self.foo = foo
      
      def f() -> C[str]:
          return C('test')
      
      x = f()
      expr = x.foo
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `multi-type explicitly parametrized generic class instance with PEP695 syntax`() = test("""
      class FirstType[T]: pass
      class SecondType[V]: pass
      class ThirdType[Z]: pass
      class Clazz[T, V, Z](FirstType[T], SecondType[V], ThirdType[Z]):
          first: T
          second: V
          third: Z
      
          def __init__(self):
              pass
      
      node = Clazz[str, int, float]()
      expr = node.third
      #└ TYPE float | int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic user function with many params and nested call with PEP695 syntax`() = test("""
      def myid[T](x: T) -> T:
          pass
      
      def f[T, U, V](x: T, y: U, z: V):
          return myid(x), myid(y), myid(z)
      
      expr = f(True, 1, 'foo')
      #└ TYPE tuple[bool, int, str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic substitution in deep hierarchy with PEP695 syntax`() = test("""
      class Root[T1, T2]:
          def m(self) -> T2:
              pass
      
      class Base3[T1](Root[T1, int]):
          pass
      
      class Base2[T1](Base3[T1]):
          pass
      
      class Base1[T1](Base2[T1]):
          pass
      
      class Sub[T1](Base1[T1]):
          pass
      
      expr = Sub().m()
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic class specializes inherited parameter and adds new one with PEP695 syntax`() = test("""
      class Box[T]:
          pass
      
      class StrBoxWithExtra[T2](Box[str]):
          def __init__(self, extra: T2):
              self.extra = extra
      
      expr = StrBoxWithExtra(42)
      #└ TYPE StrBoxWithExtra[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `simple type alias with PEP695 syntax`() = test("""
      type myType = str
      def foo() -> myType:
          pass
      expr = foo()
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic type alias for tuple with PEP695 syntax`() = test("""
      type Pair[T] = tuple[T, T]
      expr: Pair[int]
      #└ TYPE tuple[int, int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic type alias parameterized in two steps with PEP695 syntax`() = test("""
      type Alias1[T1, T2] = dict[T1, T2]
      type Alias2[T2] = Alias1[int, T2]
      expr: Alias2[str]
      #└ TYPE dict[int, str]
      """.trimIndent())

    @Test
    fun `unconstrained TypeVar default Any`() = test("""
      from typing import Any

      def f[T=Any]() -> T: ...

      expr = f()
      #└ TYPE Any
      """.trimIndent())

    @Test
    fun `unconstrained TypeVar`() = test("""
      def f[T]() -> T: ...

      expr = f()
      #└ TYPE T
      """.trimIndent())
  }

  @Nested
  inner class TypeVarDefaultsPep696 {
    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class reference`() = test("""
      from typing import TypeVar, Generic
      StartT = TypeVar("StartT", default=int)
      StopT = TypeVar("StopT", default=StartT)
      StepT = TypeVar("StepT", default=int | None)
      class slice(Generic[StartT, StopT, StepT]): ...
      expr = slice
      #└ TYPE type[slice[int, int, int | None]]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class reference new syntax`() = test("""
      class slice[StartT = int, StopT = StartT, StepT = int | None]: ...
      expr = slice
      #└ TYPE type[slice[int, int, int | None]]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class call`() = test("""
      from typing import TypeVar, Generic
      StartT = TypeVar("StartT", default=int)
      StopT = TypeVar("StopT", default=StartT)
      StepT = TypeVar("StepT", default=int | None)
      class slice(Generic[StartT, StopT, StepT]): ...
      expr = slice()
      #└ TYPE slice[int, int, int | None]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class call new syntax`() = test("""
      class slice[StartT = int, StopT = StartT, StepT = int | None]: ...
      expr = slice()
      #└ TYPE slice[int, int, int | None]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class call parameterized with one type`() = test("""
      from typing import TypeVar, Generic
      StartT = TypeVar("StartT", default=int)
      StopT = TypeVar("StopT", default=StartT)
      StepT = TypeVar("StepT", default=int | None)
      class slice(Generic[StartT, StopT, StepT]): ...
      expr = slice[str]()
      #└ TYPE slice[str, str, int | None]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class call fully parameterized`() = test("""
      from typing import TypeVar, Generic
      StartT = TypeVar("StartT", default=int)
      StopT = TypeVar("StopT", default=StartT)
      StepT = TypeVar("StepT", default=int | None)
      class slice(Generic[StartT, StopT, StepT]): ...
      expr = slice[str, bool, complex]()
      #└ TYPE slice[str, bool, complex | float | int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults list default`() = test("""
      from typing import TypeVar, Generic

      T = TypeVar("T")
      ListDefaultT = TypeVar("ListDefaultT", default=list[T])

      class Bar(Generic[T, ListDefaultT]):
          def __init__(self, x: T, y: ListDefaultT): ...

      expr = Bar[int]
      #└ TYPE type[Bar[int, list[int]]]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class with init method reference new syntax`() = test("""
      from typing import TypeVar, Generic
      class Bar[Z1, ListDefaultT = list[Z1]]:
          def __init__(self, x: Z1, y: ListDefaultT): ...
      expr = Bar
      #└ TYPE type[Bar[Unknown, list[Unknown]]]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class with init method call parameterized with two types and constructor arguments`() = test("""
      from typing import TypeVar, Generic
      Z1 = TypeVar("Z1")
      ListDefaultT = TypeVar("ListDefaultT", default=list[Z1])
      class Bar(Generic[Z1, ListDefaultT]):
          def __init__(self, x: Z1, y: ListDefaultT): ...
      expr = Bar[int, list[str]](0, [])
      #└ TYPE Bar[int, list[str]]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults subclassed class reference`() = test("""
      from typing import TypeVar, Generic, TypeAlias
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      class SubclassMe(Generic[T1, DefaultStrT]):
          x: DefaultStrT
      class Bar(SubclassMe[int, DefaultStrT]): ...
      expr = Bar
      #└ TYPE type[Bar[str]]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults subclassed parameterized class instance`() = test("""
      from typing import TypeVar, Generic, TypeAlias
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      class SubclassMe(Generic[T1, DefaultStrT]):
          x: DefaultStrT
      class Bar(SubclassMe[int, DefaultStrT]): ...
      expr = Bar[bool]()
      #└ TYPE Bar[bool]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults subclassed with class attribute`() = test("""
      from typing import TypeVar, Generic, TypeAlias
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      class SubclassMe(Generic[T1, DefaultStrT]):
          x: DefaultStrT
      class Foo(SubclassMe[float]): ...
      expr = Foo().x
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar with defaults method return type`() = test("""
      from typing import TypeVar, Generic
      DefaultIntT = TypeVar('DefaultIntT', default=int)
      class Test(Generic[DefaultIntT]):
          def foo(self) -> DefaultIntT: ...
      expr = Test().foo()
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults empty constructor call`() = test("""
      from typing import TypeVar, Generic
      T = TypeVar("T", default=int)
      T1 = TypeVar("T1", default=str)
      T2 = TypeVar("T2", default=bool)
      class Box(Generic[T, T1, T2]):
          def __init__(self, a: T = None, b: T1 = None, c: T2 = None):
              self.value = a
              self.value1 = b
              self.value2 = c
      expr = Box()
      #└ TYPE Box[int, str, bool]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style generic function with default`() = test("""
      def foo[T = int](x: T = None) -> T: ...
      expr = foo()
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `reference to TypeVarTuple with default is parameterized type`() = test("""
      from typing import Generic, TypeVarTuple, Unpack
      DefaultTs = TypeVarTuple("DefaultTs", default=Unpack[tuple[str, int]])
      class Foo(Generic[*DefaultTs]): ...
      expr = Foo
      #└ TYPE type[Foo[str, int]]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVarTuple with default`() = test("""
      from typing import Generic, TypeVarTuple, Unpack
      DefaultTs = TypeVarTuple("DefaultTs", default=Unpack[tuple[str, int]])
      class Foo(Generic[*DefaultTs]): ...
      expr = Foo()
      #└ TYPE Foo[str, int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVarTuple with defaults class instance new syntax`() = test("""
      from typing import Unpack
      class Foo[*DefaultTs = Unpack[tuple[str, int]]]: ...
      expr = Foo()
      #└ TYPE Foo[str, int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVarTuple with default overriden by explicit`() = test("""
      from typing import Generic, TypeVarTuple, Unpack
      DefaultTs = TypeVarTuple("DefaultTs", default=Unpack[tuple[str, int]])
      class Foo(Generic[*DefaultTs]): ...
      expr = Foo[bool, float]()
      #└ TYPE Foo[bool, float | int]
      """.trimIndent())
  }

  @Test
  @TestFor(issues = ["PY-32375"])
  fun `returning str against bounded TypeVar return`() = test("""
    from typing import TypeVar

    F = TypeVar('F', bound=int)

    def deco(func: F) -> F:
        return "" # WARNING Expected type 'F ≤: int', got 'Literal[""]' instead
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-32313"])
  fun `matching against constrained TypeVar in type`() = test("""
    from typing import Type, TypeVar

    class A:
        pass

    class B(A):
        pass

    class C:
        pass

    T = TypeVar('T', A, B)

    def f(cls: Type[T], arg: int) -> T:
        pass

    f(A, 1)
    f(B, 2)
    f(C, 3) # WARNING Expected type 'type[T ≤: A | B]', got 'type[C]' instead
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-33500"])
  fun `implicit generic dunder call on typed element`() = test("""
    from typing import TypeVar, Generic

    _T = TypeVar('_T')

    class Callback(Generic[_T]):
        def __call__(self, arg: _T):
            pass

    def foo(cb: Callback[int]):
        cb("42") # WARNING Expected type 'int', got 'Literal["42"]' instead
    """.trimIndent())

  @Test
  fun `generic callable parameter mapped by another argument`() = test("""
    from typing import Callable, TypeVar

    T = TypeVar('T')

    def func(x: T, c: Callable[[T], None]) -> None:
        pass

    def accepts_anything(x: str) -> None:
        pass

    # FIXME PY-37876: an error is expected here but is not produced; documents current behavior.
    func(42, accepts_anything)
    """.trimIndent())

}

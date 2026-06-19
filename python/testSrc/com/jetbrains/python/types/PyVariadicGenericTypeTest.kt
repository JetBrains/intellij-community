// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for variadic generics ([TypeVarTuple][https://peps.python.org/pep-0646/]).
 */
class PyVariadicGenericTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class InferenceBasicForms {

    @Test
    fun `variadic generic type`() = test("""
      from typing import Generic, TypeVarTuple, Tuple

      Shape = TypeVarTuple('Shape')

      t: Tuple[*Shape]
      #         ^^^^^ WARNING Unbound type variable
      expr = t
      # └ TYPE tuple[*Shape]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic by callable`() = test("""
      from typing import TypeVar, TypeVarTuple, Callable, Tuple
      
      Ts = TypeVarTuple('Ts')
      
      
      def foo(f: Callable[[*Ts], Tuple[*Ts]]) -> Tuple[*Ts]: ...
      def bar(a: int, b: str) -> Tuple[int, str]: ...
      
      
      expr = foo(bar)
      #└ TYPE tuple[int, str]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic by callable prefix suffix`() = test("""
      from typing import TypeVar, TypeVarTuple, Callable, Tuple
      
      T = TypeVar('T')
      Ts = TypeVarTuple('Ts')
      
      
      def foo(f: Callable[[int, *Ts, T], Tuple[T, *Ts]]) -> Tuple[str, *Ts, int, T]: ...
      def bar(a: int, b: str, c: float, d: bool) -> Tuple[bool, str, float]: ...
      
      
      expr = foo(bar)
      #└ TYPE tuple[str, str, float | int, int, bool]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic class`() = test("""
      from typing import TypeVarTuple, Generic, Tuple
      
      Ts = TypeVarTuple('Ts')
      
      
      class A(Generic[*Ts]):
          def __init__(self, value: Tuple[int, *Ts]) -> None:
              self.field: Tuple[int, *Ts] = value
      
      
      tpl = (42, 1.1, True, ['42'])
      expr = A(tpl)
      # └ TYPE A[float | int, bool, list[str]]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic class field`() = test("""
      from typing import TypeVarTuple, Generic, Tuple
      
      Ts = TypeVarTuple('Ts')
      
      
      class A(Generic[*Ts]):
          def __init__(self, value: Tuple[int, *Ts]) -> None:
              self.field: Tuple[int, *Ts] = value
      
      
      tpl = (42, 1.1, True, ['42'])
      a = A(tpl)
      expr = a.field
      #└ TYPE tuple[int, float | int, bool, list[str]]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic class method`() = test("""
      from typing import TypeVarTuple, Generic, Tuple
      
      Ts = TypeVarTuple('Ts')
      
      
      class A(Generic[*Ts]):
          def __init__(self, value: Tuple[*Ts]) -> None:
              ...
      
          def foo(self) -> Tuple[int, *Ts, str]:
              ...
      
      
      tpl = (True, 1.1)
      a = A(tpl)
      expr = a.foo()
      #└ TYPE tuple[int, bool, float | int, str]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic class method plus`() = test("""
      from __future__ import annotations
      from typing import TypeVarTuple, Generic, Tuple, TypeVar
      
      T = TypeVar('T')
      Ts = TypeVarTuple('Ts')
      
      
      class A(Generic[T, *Ts]):
          def __init__(self, t: T, *args: *Ts) -> None:
              ...
      
          def __add__(self, other: A[T, *Ts]) -> A[T, *Ts, T]:
              ...
      
      
      a = A(1, '', True)
      b = A(1, '', True)
      expr = a + b
      #└ TYPE A[int, str, bool, int]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic and generic class`() = test("""
      from __future__ import annotations
      from typing import TypeVarTuple, Generic, Tuple, TypeVar
      
      T = TypeVar('T')
      T1 = TypeVar('T1')
      Ts = TypeVarTuple('Ts')
      
      
      class A(Generic[T, *Ts, T1]):
          def __init__(self, t: T, tpl: Tuple[*Ts], t1: T1) -> None:
              ...
      
      
      x: int | str
      expr = A(x, (x,), [1])
      #└ TYPE A[int | str, int | str, list[int]]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic class method add axis prefix`() = test("""
      from __future__ import annotations
      from typing import Generic, TypeVarTuple, Tuple, NewType, TypeVar
      
      T = TypeVar('T')
      Shape = TypeVarTuple('Shape')
      
      
      class Array(Generic[*Shape]):
          def __init__(self, shape: Tuple[*Shape]):
              self._shape: Tuple[*Shape] = shape
      
          def add_axis_prefix(self, t: T) -> Array[T, *Shape]: ...
      
      
      shape = (42, True)
      arr: Array[int, bool] = Array(shape)
      expr = arr.add_axis_prefix('')
      #└ TYPE Array[str, int, bool]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic class method add axis suffix`() = test("""
      from __future__ import annotations
      from typing import Generic, TypeVarTuple, Tuple, NewType, TypeVar
      
      T = TypeVar('T')
      Shape = TypeVarTuple('Shape')
      
      
      class Array(Generic[*Shape]):
          def __init__(self, shape: Tuple[*Shape]):
              self._shape: Tuple[*Shape] = shape
      
          def add_axis_suffix(self, t: T) -> Array[*Shape, T]: ...
      
      
      shape = ([42], True)
      arr: Array[list[int], bool] = Array(shape)
      expr = arr.add_axis_suffix('42')
      #└ TYPE Array[list[int], bool, str]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic class method add axis prefix and suffix`() = test("""
      from __future__ import annotations
      from typing import Generic, TypeVarTuple, Tuple, NewType, TypeVar
      
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      T3 = TypeVar('T3')
      T4 = TypeVar('T4')
      Shape = TypeVarTuple('Shape')
      
      
      class Array(Generic[*Shape]):
          def __init__(self, shape: Tuple[*Shape]):
              self._shape: Tuple[*Shape] = shape
      
          def add_axis_prefix_suffix(self, t1: T1, t2: T2, t3: T3, t4: T4) -> Array[T3, T2, *Shape, T1, T4]: ...
      
      
      shape = (42, '42')
      arr: Array[int, str] = Array(shape)
      expr = arr.add_axis_prefix_suffix([42], {42: '42'}, '42', True)
      #└ TYPE Array[str, dict[int, str], int, str, list[int], bool]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic function add prefix and suffix`() = test("""
      from typing import Generic, TypeVarTuple, NewType, Tuple
      
      Ts = TypeVarTuple('Ts')
      
      
      class Array(Generic[*Ts]):
          def __init__(self, shape: Tuple[*Ts]):
              ...
      
      
      def add_suf_pref(x: Array[*Ts]) -> Array[int, *Ts, str]:
          ...
      
      
      ts = ([42], True)
      arr = Array(ts)
      expr = add_suf_pref(arr)
      #└ TYPE Array[int, list[int], bool, str]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic function delete prefix and suffix`() = test("""
      from typing import Generic, TypeVarTuple, NewType, Tuple
      
      Ts = TypeVarTuple('Ts')
      
      
      class Array(Generic[*Ts]):
          def __init__(self, shape: Tuple[*Ts]):
              ...
      
      
      def del_suf_pref(x: Array[int, *Ts, str]) -> Array[*Ts]:
          ...
      
      
      ts = (42, [42], True, '42')
      arr = Array(ts)
      expr = del_suf_pref(arr)
      # └ TYPE Array[list[int], bool]
      """)
  }

  @Nested
  inner class InferenceStarArgs {

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic star args`() = test("""
      from typing import TypeVarTuple, Tuple
      
      Ts = TypeVarTuple('Ts')
      
      
      def args_to_tuple(*args: *Ts) -> Tuple[*Ts]: ...
      
      
      expr = args_to_tuple(1, 'a')
      #└ TYPE tuple[int, str]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic star args of generic variadics`() = test("""
      from typing import Tuple, TypeVarTuple
      
      Ts = TypeVarTuple('Ts')
      
      
      def foo(*args: Tuple[*Ts]) -> Tuple[*Ts]: ...
      
      
      expr = foo((0, '1'), (1, '0'))
      #└ TYPE tuple[int, str]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic star args prefix suffix`() = test("""
      from typing import TypeVarTuple, Tuple
      
      Ts = TypeVarTuple('Ts')
      
      
      def foo(*args: *Tuple[int, *Ts, str]) -> Tuple[*Ts, int]: ...
      
      
      expr = foo(1, '', [], {}, True, '')
      #└ TYPE tuple[str, list[Unknown], dict[Unknown, Unknown], bool, int]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic star args and type vars`() = test("""
      from typing import TypeVarTuple, Tuple, TypeVar
      
      Ts = TypeVarTuple('Ts')
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      
      
      def args_to_tuple(t1: T1, t2: T2, *args: *Tuple[T2, *Ts, float]) -> Tuple[T2, *Ts, T1]: ...
      
      
      expr = args_to_tuple(1, 'a', 'a', [1], True, 3.3)
      #└ TYPE tuple[str, list[int], bool, int]
      """)
  }

  @Nested
  inner class InferenceTypeAliases {

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic type alias`() = test("""
      from typing import Tuple, TypeVarTuple
      
      Ts = TypeVarTuple('Ts')
      
      MyType = Tuple[int, *Ts]
      
      t: MyType[str, bool]
      expr = t
      #└ TYPE tuple[int, str, bool]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic and generic type alias`() = test("""
      from typing import Tuple, TypeVarTuple, TypeVar
      
      T = TypeVar('T')
      Ts = TypeVarTuple('Ts')
      
      MyType = Tuple[int, *Ts, T]
      
      t: MyType[str, bool, float]
      expr = t
      #└ TYPE tuple[int, str, bool, float | int]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic and generic consecutive type alias`() = test("""
      from typing import Tuple, TypeVarTuple, TypeVar
      
      T = TypeVar('T')
      Ts = TypeVarTuple('Ts')
      
      MyType = Tuple[int, T, *Ts]
      MyType1 = MyType[str, *Ts]
      
      t: MyType1[list[str], dict[str, int]]
      expr = t
      #└ TYPE tuple[int, str, list[str], dict[str, int]]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `chain of generic aliases with TypeVarTuple replaced by generic unpacked tuple`() = test("""
      from typing import Tuple, TypeVarTuple, TypeVar
      
      T = TypeVar('T')
      Ts = TypeVarTuple('Ts')
      Ts1 = TypeVarTuple('Ts1')
      
      MyType = Tuple[int, T, *Ts]
      MyType1 = MyType[str, bool, *Ts1]
      
      t: MyType1[list[str], dict[str, int]]
      expr = t
      #└ TYPE tuple[int, str, bool, list[str], dict[str, int]]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics intersects same name`() = test("""
      from typing import Tuple, TypeVarTuple, TypeVar
      
      T = TypeVar('T')
      Ts = TypeVarTuple('Ts')
      
      MyType = Tuple[int, T, *Ts]
      MyType1 = MyType[str, bool, *Ts]
      
      t: MyType1[list[str], dict[str, int]]
      expr = t
      #└ TYPE tuple[int, str, bool, list[str], dict[str, int]]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics tuple unpacking`() = test("""
      from typing import Tuple, TypeVarTuple, TypeVar
      Ts = TypeVarTuple('Ts')
      MyType = Tuple[int, *Ts]
      t: MyType[*tuple[str, bool, float]]
      expr = t
      #└ TYPE tuple[int, str, bool, float | int]
      """)
  }

  @Nested
  inner class InferenceHomogeneousUnboundGenericVariadics {

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic match with homogeneous generic variadic and other types`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")

      class Array(Generic[*Shape]):
          ...

      y: Array[int, *tuple[Any, ...], int, str] = Array()

      def expect_variadic_array(x: Array[int, *Shape]) -> Array[*Shape]:
      #                                                   ^^^^^^^^^^^^^ WARNING Expected type 'Array[*Shape]', got 'None' instead
          print(x)

      expr = expect_variadic_array(y)
      #└ TYPE Array[*tuple[Any, ...], int, str]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic match with homogeneous generic variadic and other types prefix suffix`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      T = TypeVar("T")
      T1 = TypeVar("T1")

      class Array(Generic[*Shape]):
          ...

      y: Array[int, float, *tuple[Any, ...], int, str] = Array()

      def expect_variadic_array(x: Array[int, T, *Shape, T1]) -> Array[*Shape, T, T1]:
      #                                                          ^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'Array[*Shape, T, T1]', got 'None' instead
          print(x)

      expr = expect_variadic_array(y)
      #└ TYPE Array[*tuple[Any, ...], int, float | int, str]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic match with homogeneous generic variadic ambiguous match actual generic first`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      T = TypeVar("T")
      T1 = TypeVar("T1")

      class Array(Generic[*Shape]):
          ...

      y: Array[int, *tuple[float, ...], int, str] = Array()

      def expect_variadic_array(x: Array[int, T, *Shape, T1]) -> Array[*Shape, T, T1]:
      #                                                          ^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'Array[*Shape, T, T1]', got 'None' instead
          print(x)

      expr = expect_variadic_array(y)
      #└ TYPE Array[*tuple[float | int, ...], int, float | int, str]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics not unified both ambiguous match`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      T = TypeVar("T")
      T1 = TypeVar("T1")

      class Array(Generic[*Shape]):
          ...

      y: Array[*tuple[int, ...], int, str] = Array()

      def expect_variadic_array(x: Array[int, T, *Shape, T1]) -> Array[*Shape, T, T1]:
      #                                                          ^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'Array[*Shape, T, T1]', got 'None' instead
          print(x)

      expr = expect_variadic_array(y)
      #└ TYPE Array[*tuple[int, ...], int, int, str]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics not unified both actual homogeneous generic first`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      T = TypeVar("T")

      class Array(Generic[*Shape]):
          ...

      y: Array[*tuple[float, ...]] = Array()

      def expect_variadic_array(x: Array[T, *Shape]) -> Array[T, *Shape]:
      #                                                 ^^^^^^^^^^^^^^^^ WARNING Expected type 'Array[T, *Shape]', got 'None' instead
          print(x)

      expr = expect_variadic_array(y)
      #└ TYPE Array[float | int, *tuple[float | int, ...]]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics not unified both actual homogeneous generic last`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      T = TypeVar("T")

      class Array(Generic[*Shape]):
          ...

      y: Array[*tuple[float, ...]] = Array()

      def expect_variadic_array(x: Array[*Shape, T]) -> Array[*Shape, T]:
      #                                                 ^^^^^^^^^^^^^^^^ WARNING Expected type 'Array[*Shape, T]', got 'None' instead
          print(x)

      expr = expect_variadic_array(y)
      #└ TYPE Array[*tuple[float | int, ...], float | int]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics not unified both actual homogeneous generics both sides`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      T = TypeVar("T")
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      T3 = TypeVar("T3")

      class Array(Generic[*Shape]):
          ...

      y: Array[*tuple[float, ...]] = Array()

      def expect_variadic_array(x: Array[T1, *Shape, T2, T3]) -> Array[T1, *Shape, T2, T3]:
      #                                                          ^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'Array[T1, *Shape, T2, T3]', got 'None' instead
          print(x)

      expr = expect_variadic_array(y)
      #└ TYPE Array[float | int, *tuple[float | int, ...], float | int, float | int]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics not unified both same expected and actual`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      Shape1 = TypeVarTuple("Shape1")
      T = TypeVar("T")
      T1 = TypeVar("T1")

      class Array(Generic[*Shape]):
          ...

      y: Array[int, *Shape, str] = Array()
      #              ^^^^^ WARNING Unbound type variable

      def expect_variadic_array(x: Array[int, *Shape1, str]) -> Array[*Shape1]:
      #                                                         ^^^^^^^^^^^^^^ WARNING Expected type 'Array[*Shape1]', got 'None' instead
          print(x)

      expr = expect_variadic_array(y)
      #└ TYPE Array[*Shape]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics not unified both expected expand`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      Shape1 = TypeVarTuple("Shape1")
      T = TypeVar("T")
      T1 = TypeVar("T1")

      class Array(Generic[*Shape]):
          ...

      y: Array[int, float, *Shape, list[str], str] = Array()
      #                     ^^^^^ WARNING Unbound type variable

      def expect_variadic_array(x: Array[int, *Shape1, str]) -> Array[*Shape1]:
      #                                                         ^^^^^^^^^^^^^^ WARNING Expected type 'Array[*Shape1]', got 'None' instead
          print(x)

      expr = expect_variadic_array(y)
      #└ TYPE Array[float | int, *Shape, list[str]]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics not unified both expected expand two arguments`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      Shape1 = TypeVarTuple("Shape1")
      T = TypeVar("T")
      T1 = TypeVar("T1")

      class Array(Generic[*Shape]):
          ...

      a: Array[int, float, *Shape, list[str], str] = Array()
      #                     ^^^^^ WARNING Unbound type variable

      def expect_variadic_arrays(x: Array[int, *Shape1, str], y: Array[int, float, bool, list[str], str]) -> Array[*Shape1]:
      #                                                                                                      ^^^^^^^^^^^^^^ WARNING Expected type 'Array[*Shape1]', got 'None' instead
          print(x, y)

      expr = expect_variadic_arrays(a, a)
      #│                               └ WARNING Expected type 'Array[int, float | int, bool, list[str], str]', got 'Array[int, float | int, *Shape, list[str], str]' instead
      #└ TYPE Unknown
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics not unified both expected expand two arguments generic variadic`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      Shape1 = TypeVarTuple("Shape1")
      T = TypeVar("T")
      T1 = TypeVar("T1")

      class Array(Generic[*Shape]):
          ...

      a: Array[int, float, *Shape, list[str], str] = Array()
      #                     ^^^^^ WARNING Unbound type variable

      def expect_variadic_arrays(x: Array[int, *Shape1, str], y: Array[int, float, *Shape1, list[str], str]) -> Array[*Shape1]:
      #                                                                                                         ^^^^^^^^^^^^^^ WARNING Expected type 'Array[*Shape1]', got 'None' instead
          print(x, y)

      expr = expect_variadic_arrays(a, a)
      #│                               └ WARNING Expected type 'Array[int, float | int, float | int, *Shape, list[str], list[str], str]' (matched generic type 'Array[int, float | int, *Shape1, list[str], str]'), got 'Array[int, float | int, *Shape, list[str], str]' instead
      #└ TYPE Unknown
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics not unified both expected expand two different arguments generic variadic`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      Shape1 = TypeVarTuple("Shape1")
      T = TypeVar("T")
      T1 = TypeVar("T1")

      class Array(Generic[*Shape]):
          ...

      a: Array[int, float, *Shape, list[str], str] = Array()
      #                     ^^^^^ WARNING Unbound type variable

      def expect_variadic_arrays(x: Array[int, *Shape1, str], y: Array[int, float, *Shape, bool, list[str], str]) -> Array[*Shape1] | Array[*Shape]:
      #                                                                                                              ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'Array[*Shape1] | Array[*Shape]', got 'None' instead
          print(x, y)

      expr = expect_variadic_arrays(a, a)
      #│                               └ WARNING Expected type 'Array[int, float | int, *Shape, bool, list[str], str]', got 'Array[int, float | int, *Shape, list[str], str]' instead
      #└ TYPE Unknown
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics not unified both expected expand not exact left`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      Shape1 = TypeVarTuple("Shape1")
      T = TypeVar("T")
      T1 = TypeVar("T1")

      class Array(Generic[*Shape]):
          ...

      y: Array[int, *Shape, list[str], str] = Array()
      #              ^^^^^ WARNING Unbound type variable

      def expect_variadic_array(x: Array[int, float, *Shape1, str]) -> Array[*Shape1]:
      #                                                                ^^^^^^^^^^^^^^ WARNING Expected type 'Array[*Shape1]', got 'None' instead
          print(x)

      expr = expect_variadic_array(y)
      #│                           └ WARNING Expected type 'Array[int, float | int, *Shape1, str]', got 'Array[int, *Shape, list[str], str]' instead
      #└ TYPE Unknown
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics not unified both expected expand not exact right`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      Shape1 = TypeVarTuple("Shape1")
      T = TypeVar("T")
      T1 = TypeVar("T1")

      class Array(Generic[*Shape]):
          ...

      y: Array[int, float, *Shape, str] = Array()
      #                     ^^^^^ WARNING Unbound type variable

      def expect_variadic_array(x: Array[int, *Shape1, int, str]) -> Array[*Shape1]:
      #                                                              ^^^^^^^^^^^^^^ WARNING Expected type 'Array[*Shape1]', got 'None' instead
          print(x)

      expr = expect_variadic_array(y)
      #│                           └ WARNING Expected type 'Array[int, *Shape1, int, str]', got 'Array[int, float | int, *Shape, str]' instead
      #└ TYPE Unknown
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadics not unified both actual swallow all expected`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import Any

      Shape = TypeVarTuple("Shape")
      Shape1 = TypeVarTuple("Shape1")
      T = TypeVar("T")
      T1 = TypeVar("T1")

      class Array(Generic[*Shape]):
          ...

      y: Array[*Shape] = Array()
      #         ^^^^^ WARNING Unbound type variable

      def expect_variadic_array(x: Array[int, *Shape1, str]) -> Array[*Shape1]:
      #                                                         ^^^^^^^^^^^^^^ WARNING Expected type 'Array[*Shape1]', got 'None' instead
          print(x)

      expr = expect_variadic_array(y)
      #│                           └ WARNING Expected type 'Array[int, *Shape1, str]', got 'Array[*Shape]' instead
      #└ TYPE Unknown
      """)
  }

  @Nested
  inner class InferenceMisc {

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `weak union type of generic variadic method call receiver`() = test(
      // The original `PyTypingTest` did not assert weak warnings. The new framework surfaces a
      // `Member 'int' of ... does not have attribute 'get'` weak warning whose multi-word
      // "WEAK WARNING" severity name cannot be expressed as a comment-span assertion; disable weak
      // warnings to stay faithful to the original expectation.
      defaultTestOptions.copy(enableWeakWarnings = false),
      """
      from typing import Any, Generic, TypeVarTuple, Tuple

      Ts = TypeVarTuple("Ts")

      class Box(Generic[*Ts]):
          def get(self) -> Tuple[*Ts]:
              pass

      class StrBox(Box[str, int, float]):
          pass

      receiver: Any | int | StrBox = ...
      expr = receiver.get()
      #└ TYPE tuple[str, int, float | int]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic class type hinted in docstrings`() = test("""
      from typing import Generic, TypeVar, TypeVarTuple, Tuple
      
      Ts = TypeVarTuple('Ts')
      
      class User1(Generic[*Ts]):
          def __init__(self, x: Tuple[*Ts]):
              self.x = x
      
          def get(self) -> Tuple[*Ts]:
              return self.x
      
      c = User1((1, '2', 3.3))
      expr = c.get()
      #└ TYPE tuple[int, str, float | int]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic decorator with argument called as function`() = test("""
      from typing import Callable, TypeVar, TypeVarTuple, Tuple
      
      Ss = TypeVarTuple('Ss')
      Ts = TypeVarTuple('Ts')
      
      def dec(ts: Tuple[*Ts]):
          def g(fun: Callable[[], Tuple[*Ss]]) -> Callable[[*Ts], Tuple[*Ss]]:
              ...
      
          return g
      
      def func() -> Tuple[int, str, float]:
          ...
      
      expr = dec(('foo', 42))(func)
      #└ TYPE (str, int) -> tuple[int, str, float | int]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic parameter of expected callable`() = test("""
      from typing import Callable, Generic, TypeVar, TypeVarTuple, Tuple
      
      Ts = TypeVarTuple('Ts')
      
      class Super(Generic[*Ts]):
          pass
      
      class Sub(Super[*Ts]):
          pass
      
      def f(x: Callable[[Sub[*Ts]], None]) -> Tuple[*Ts]:
          pass
      
      def g(x: Super[int, str, float]):
          pass
      
      expr = f(g)
      #└ TYPE tuple[int, str, float | int]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic iterator parameterized with another generic variadic`() = test("""
      from typing import Iterator, Generic, Tuple, TypeVarTuple
      
      class Entry[*Ts]:
          pass
      
      class MyIterator[*Ts](Iterator[Entry[*Ts]]):
          def __next__(self) -> Entry[*Ts]: ...
      
      def iter_entries[*Ts](path: Tuple[*Ts]) -> MyIterator[*Ts]: ...
      
      def main() -> None:
          for x in iter_entries(("some path", 1, 1.1)):
              expr = x
      #       └ TYPE Entry[str, int, float | int]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `generic variadic parameterized with generic variadic`() = test("""
      from typing import Generic, TypeVar, TypeVarTuple, Tuple

      Ts = TypeVarTuple('Ts')


      class Box(Generic[*Ts]):
          def get(self) -> Tuple[*Ts]:
              pass


      class ListBox(Box[Tuple[*Ts]]):
          pass


      xs: ListBox[int, str] = ...
      #                       ^^^ WARNING Expected type 'ListBox[int, str]', got 'EllipsisType' instead
      expr = xs.get()
      #└ TYPE tuple[tuple[int, str]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVarTuple with default parameterized with another generic`() = test("""
      from typing import Generic, TypeVarTuple, Unpack, TypeVar
      T = TypeVar("T", default=list)
      DefaultTs = TypeVarTuple("DefaultTs", default=Unpack[tuple[T, int]])
      class Foo(Generic[T, *DefaultTs]): ...
      expr = Foo()
      #└ TYPE Foo[list[Unknown], list[Unknown], int]
      """)
  }

  @Nested
  inner class InferencePEP695Syntax {

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic variadic by callable PEP695`() = test("""
      from typing import Callable, Tuple
      
      def foo[*Ts](f: Callable[[*Ts], Tuple[*Ts]]) -> Tuple[*Ts]: ...
      
      def bar(a: int, b: str) -> Tuple[int, str]: ...
      
      expr = foo(bar)
      #└ TYPE tuple[int, str]
      """)

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic variadic by callable prefix suffix PEP695`() = test("""
      from typing import Callable, Tuple
      
      def foo[T, *Ts](f: Callable[[int, *Ts, T], Tuple[T, *Ts]]) -> Tuple[str, *Ts, int, T]: ...
      
      def bar(a: int, b: str, c: float, d: bool) -> Tuple[bool, str, float]: ...
      
      expr = foo(bar)
      #└ TYPE tuple[str, str, float | int, int, bool]
      """)

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic variadic class PEP695`() = test("""
      from typing import Generic, Tuple
      
      class A[*Ts]:
          def __init__(self, value: Tuple[int, *Ts]) -> None:
              self.field: Tuple[int, *Ts] = value
      
      tpl = (42, 1.1, True, ['42'])
      expr = A(tpl)
      #└ TYPE A[float | int, bool, list[str]]
      """)

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic variadic class field PEP695`() = test("""
      from typing import Tuple
      
      class A[*Ts]:
          def __init__(self, value: Tuple[int, *Ts]) -> None:
              self.field: Tuple[int, *Ts] = value
      
      tpl = (42, 1.1, True, ['42'])
      a = A(tpl)
      expr = a.field
      #└ TYPE tuple[int, float | int, bool, list[str]]
      """)

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic variadic and generic class PEP695`() = test("""
      from __future__ import annotations
      from typing import Tuple
      
      
      class A[T, *Ts, T1]:
          def __init__(self, t: T, tpl: Tuple[*Ts], t1: T1) -> None:
              ...
      
      x: int | str
      expr = A(x, (x,), [1])
      #└ TYPE A[int | str, int | str, list[int]]
      """)

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic variadic class method add axis prefix PEP695`() = test("""
      from __future__ import annotations
      from typing import Tuple, NewType
      
      class Array[*Shape]:
          def __init__(self, shape: Tuple[*Shape]):
              self._shape: Tuple[*Shape] = shape
      
          def add_axis_prefix[T](self, t: T) -> Array[T, *Shape]: ...
      
      shape = (42, True)
      arr: Array[int, bool] = Array(shape)
      expr = arr.add_axis_prefix('')
      #└ TYPE Array[str, int, bool]
      """)

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic variadic star args and type vars PEP695`() = test("""
      from typing import TypeVarTuple, Tuple, TypeVar
      
      def args_to_tuple[T1, T2, *Ts](t1: T1, t2: T2, *args: *Tuple[T2, *Ts, float]) -> Tuple[T2, *Ts, T1]: ...
      
      expr = args_to_tuple(1, 'a', 'a', [1], True, 3.3)
      #└ TYPE tuple[str, list[int], bool, int]
      """)
  }

  @Nested
  inner class InspectionsTypeCheckerWarnings {

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic in function`() = test("""
      from typing import Tuple, TypeVarTuple, TypeVar
      
      T = TypeVar('T')
      Ts = TypeVarTuple('Ts')
      
      
      def foo(x: T, y: Tuple[*Ts]):
          pass
      
      
      foo(10, (1, '1', [1]))
      """)

    @Test
    @TestFor(issues = ["PY-63820"])
    fun `variadic generic empty args call`() = test("""
      from typing import TypeVarTuple
      
      Ts = TypeVarTuple('Ts')
      
      
      def foo(*args: *Ts) -> None:
          pass
      
      
      foo()
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic argument by callable in function`() = test("""
      from typing import Callable, TypeVarTuple, Tuple
      
      Ts = TypeVarTuple('Ts')
      
      def foo(a: int, f: Callable[[*Ts], None], args: Tuple[*Ts]) -> None: ...
      def bar(a: int, b: str) -> None: ...
      def baz(a: int, b: str, c: float, d: bool) -> None: ...
      
      
      foo(1, bar, args=(0, 'foo'))
      foo(1, baz, args=(0, 'foo', 1.0, False))
      
      foo(1, bar, args=('foo', 0)) # WARNING Expected type 'tuple[int, str]' (matched generic type 'tuple[*Ts]'), got 'tuple[Literal['foo'], Literal[0]]' instead
      foo(1, baz, args=('foo', 0, 1.0, False)) # WARNING Expected type 'tuple[int, str, float | int, bool]' (matched generic type 'tuple[*Ts]'), got 'tuple[Literal['foo'], Literal[0], float, Literal[False]]' instead
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic check callable in function`() = test("""
      from typing import TypeVar, TypeVarTuple, Callable, Tuple
      
      T = TypeVar('T')
      Ts = TypeVarTuple('Ts')
      
      
      def foo(f: Callable[[int, *Ts, T], Tuple[T, *Ts]]) -> None: ...
      
      
      def ok1(a: int, b: str, c: bool, d: list[int]) -> Tuple[list[int], str, bool]: ...
      def ok2(a: int, b: str) -> Tuple[str]: ...
      
      
      foo(ok1)
      foo(ok2)
      
      
      def err1(a: int, b: str, c: bool, d: list[int]) -> Tuple[list[int], str, str]: ...
      def err2(a: int, b: str) -> Tuple[str, str]: ...
      
      
      foo(err1) # WARNING Expected type '(int, str, bool, list[int]) -> tuple[list[int], str, bool]' (matched generic type '(int, *Ts, T) -> tuple[T, *Ts]'), got '(a: int, b: str, c: bool, d: list[int]) -> tuple[list[int], str, str]' instead
      foo(err2) # WARNING Expected type '(int, str) -> tuple[str]' (matched generic type '(int, *Ts, T) -> tuple[T, *Ts]'), got '(a: int, b: str) -> tuple[str, str]' instead
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic two Ts in function`() = test("""
      from typing import TypeVarTuple, Generic
      
      Ts = TypeVarTuple('Ts')
      
      
      class Array(Generic[*Ts]):
          ...
      
      
      def foo(x: Array[*Ts], y: Array[*Ts]) -> Array[*Ts]:
          ...
      
      
      x: Array[int]
      y: Array[str]
      z: Array[int, str]
      
      foo(x, x)
      
      foo(x, y) # WARNING Expected type 'Array[int]' (matched generic type 'Array[*Ts]'), got 'Array[str]' instead
      foo(x, z) # WARNING Expected type 'Array[int]' (matched generic type 'Array[*Ts]'), got 'Array[int, str]' instead
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic unbound tuple in function`() = test("""
      from typing import Generic, TypeVarTuple, Tuple, Any
      
      Ts = TypeVarTuple('Ts')
      
      
      class Array(Generic[*Ts]):
          def __init__(self, shape: Tuple[*Ts]):
              ...
      
      
      def foo(x: Array[int, *Tuple[Any, ...], str]) -> None:
          ...
      
      
      x: Array[int, list[str], bool, str]
      foo(x)
      
      y: Array[int, str]
      foo(y)
      
      z: Array[int]
      foo(z) # WARNING Expected type 'Array[int, *tuple[Any, ...], str]', got 'Array[int]' instead
      
      t: Array[str]
      foo(t) # WARNING Expected type 'Array[int, *tuple[Any, ...], str]', got 'Array[str]' instead
      
      k: Array[int, int]
      foo(k) # WARNING Expected type 'Array[int, *tuple[Any, ...], str]', got 'Array[int, int]' instead
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic star args named parameters`() = test("""
      from typing import Tuple, TypeVarTuple

      Ts = TypeVarTuple('Ts')


      def foo(a: str, *args: *Tuple[*Ts, int], b: str, c: bool) -> None: ...


      foo('', 1, True, [1], 42, b='', c=True)
      foo('', 42, b='', c=True)
      foo('', True, 42, c=True, b='')

      foo('', b='', c=True) # WARNING Parameter 'args' unfilled, expected '*tuple[*Ts, int]'
      foo('', '', b='', c=True) # WARNING Expected type '*tuple[int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[Literal[""]]' instead
      foo('', '', [False], b='', c=True)
      #       │   ^^^^^^^ WARNING Expected type '*tuple[str, int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[Literal[""], list[bool]]' instead
      #       ^^ WARNING Expected type '*tuple[str, int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[Literal[""], list[bool]]' instead
      foo('', '', '', '', 1.1, b='', c=True)
      #       │   │   │   ^^^ WARNING Expected type '*tuple[str, str, str, int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], float]' instead
      #       │   │   ^^ WARNING Expected type '*tuple[str, str, str, int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], float]' instead
      #       │   ^^ WARNING Expected type '*tuple[str, str, str, int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], float]' instead
      #       ^^ WARNING Expected type '*tuple[str, str, str, int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], float]' instead
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic star args tuple and unpacked tuple`() = test("""
      from typing import Tuple, TypeVarTuple, Any

      Ts = TypeVarTuple('Ts')


      def foo(a: Tuple[*Ts], *args: *Tuple[str, *Ts, int], b: str) -> None: ...


      foo(('', 1), '', '', 1, 1, b='')
      foo((1,1), '', 1, 1, 1, b='')
      foo(('',), '', '', 1, b='')
      foo((), '', 1, b='')
      foo(([], {}), '', [], {}, 1, b='')

      foo(('', 1), b='') # WARNING Parameter 'args' unfilled, expected '*tuple[str, str, int, int]'
      foo(('', 1), '', '', '', 1, b='')
      #            │   │   │   └ WARNING Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], Literal[1]]' instead
      #            │   │   ^^ WARNING Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], Literal[1]]' instead
      #            │   ^^ WARNING Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], Literal[1]]' instead
      #            ^^ WARNING Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], Literal[1]]' instead
      foo((1,1), '', 1, 1, b='')
      #          │   │  └ WARNING Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      #          │   └ WARNING Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      #          ^^ WARNING Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      foo(('',), '', 1, 1, b='')
      #          │   │  └ WARNING Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      #          │   └ WARNING Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      #          ^^ WARNING Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      x: Any
      #  ^^^ ERROR Unresolved reference 'Any'
      foo((), '', 42, x, b='')
      #       │   │   └ WARNING Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[42], Any]' instead
      #       │   ^^ WARNING Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[42], Any]' instead
      #       ^^ WARNING Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[42], Any]' instead
      foo(([], {}), '', [], {}, b='')
      #             │   │   ^^ WARNING Expected type '*tuple[str, list[Unknown], dict[Unknown, Unknown], int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], list[Unknown], dict[Unknown, Unknown]]' instead
      #             │   ^^ WARNING Expected type '*tuple[str, list[Unknown], dict[Unknown, Unknown], int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], list[Unknown], dict[Unknown, Unknown]]' instead
      #             ^^ WARNING Expected type '*tuple[str, list[Unknown], dict[Unknown, Unknown], int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], list[Unknown], dict[Unknown, Unknown]]' instead
      """)

    @Test
    @TestFor(issues = ["PY-53105", "PY-76865"])
    fun `variadic generic star args of variadic generic`() = test("""
      from typing import Tuple, TypeVarTuple

      Ts = TypeVarTuple('Ts')

      def foo(*args: Tuple[*Ts]): ...

      foo((0,), (1,))
      foo((0,), (1, 2)) # WARNING Expected type 'tuple[int]' (matched generic type 'tuple[*Ts]'), got 'tuple[Literal[1], Literal[2]]' instead
      foo((0,), ('1',)) # WARNING Expected type 'tuple[int]' (matched generic type 'tuple[*Ts]'), got 'tuple[Literal['1']]' instead
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic star args of variadic generic prefix suffix`() = test("""
      from typing import Tuple, TypeVarTuple

      Ts = TypeVarTuple('Ts')


      def foo(a: Tuple[*Ts], *args: *Tuple[str, *Ts, int], b: str) -> None: ...


      foo(('', 1), '', '', 1, 1, b='')
      foo((1,1), '', 1, 1, 1, b='')
      foo(('',), '', '', 1, b='')
      foo((), '', 1, b='')
      foo(([], {}), '', [], {}, 1, b='')

      foo(('', 1), b='') # WARNING Parameter 'args' unfilled, expected '*tuple[str, str, int, int]'
      foo(('', 1), '', '', '', 1, b='')
      #            │   │   │   └ WARNING Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], Literal[1]]' instead
      #            │   │   ^^ WARNING Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], Literal[1]]' instead
      #            │   ^^ WARNING Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], Literal[1]]' instead
      #            ^^ WARNING Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], Literal[1]]' instead
      foo((1,1), '', 1, 1, b='')
      #          │   │  └ WARNING Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      #          │   └ WARNING Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      #          ^^ WARNING Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      foo(('',), '', 1, 1, b='')
      #          │   │  └ WARNING Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      #          │   └ WARNING Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      #          ^^ WARNING Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      x: Any
      #  ^^^ ERROR Unresolved reference 'Any'
      foo((), '', 42, x, b='')
      #       │   │   └ WARNING Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[42], Unknown]' instead
      #       │   ^^ WARNING Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[42], Unknown]' instead
      #       ^^ WARNING Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[42], Unknown]' instead
      foo(([], {}), '', [], {}, b='')
      #             │   │   ^^ WARNING Expected type '*tuple[str, list[Unknown], dict[Unknown, Unknown], int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], list[Unknown], dict[Unknown, Unknown]]' instead
      #             │   ^^ WARNING Expected type '*tuple[str, list[Unknown], dict[Unknown, Unknown], int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], list[Unknown], dict[Unknown, Unknown]]' instead
      #             ^^ WARNING Expected type '*tuple[str, list[Unknown], dict[Unknown, Unknown], int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], list[Unknown], dict[Unknown, Unknown]]' instead
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic star args prefix suffix`() = test("""
      from typing import Tuple, TypeVarTuple, Any

      Ts = TypeVarTuple('Ts')


      def foo(a: Tuple[*Ts], *args: *Tuple[str, *Ts, int], b: str) -> None: ...


      foo(('', 1), '', '', 1, 1, b='')
      foo((1,1), '', 1, 1, 1, b='')
      foo(('',), '', '', 1, b='')
      foo((), '', 1, b='')
      foo(([], {}), '', [], {}, 1, b='')

      foo(('', 1), b='') # WARNING Parameter 'args' unfilled, expected '*tuple[str, str, int, int]'
      foo(('', 1), '', '', '', 1, b='')
      #            │   │   │   └ WARNING Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], Literal[1]]' instead
      #            │   │   ^^ WARNING Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], Literal[1]]' instead
      #            │   ^^ WARNING Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], Literal[1]]' instead
      #            ^^ WARNING Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[""], Literal[""], Literal[1]]' instead
      foo((1,1), '', 1, 1, b='')
      #          │   │  └ WARNING Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      #          │   └ WARNING Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      #          ^^ WARNING Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      foo(('',), '', 1, 1, b='')
      #          │   │  └ WARNING Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      #          │   └ WARNING Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      #          ^^ WARNING Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[1], Literal[1]]' instead
      x: Any
      #  ^^^ ERROR Unresolved reference 'Any'
      foo((), '', 42, x, b='')
      #       │   │   └ WARNING Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[42], Any]' instead
      #       │   ^^ WARNING Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[42], Any]' instead
      #       ^^ WARNING Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], Literal[42], Any]' instead
      foo(([], {}), '', [], {}, b='')
      #             │   │   ^^ WARNING Expected type '*tuple[str, list[Unknown], dict[Unknown, Unknown], int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], list[Unknown], dict[Unknown, Unknown]]' instead
      #             │   ^^ WARNING Expected type '*tuple[str, list[Unknown], dict[Unknown, Unknown], int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], list[Unknown], dict[Unknown, Unknown]]' instead
      #             ^^ WARNING Expected type '*tuple[str, list[Unknown], dict[Unknown, Unknown], int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[Literal[""], list[Unknown], dict[Unknown, Unknown]]' instead
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic star args unbound tuple`() = test("""
      from typing import Tuple


      def foo(*args: *Tuple[int, ...]) -> None: ...


      foo()
      foo(1)
      foo(1, 2, 3)

      foo('') # WARNING Expected type 'int', got 'Literal[""]' instead
      foo(1, '') # WARNING Expected type 'int', got 'Literal[""]' instead
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic match with homogeneous generic variadic`() = test("""
      from __future__ import annotations
      
      from typing import TypeVarTuple
      from typing import Generic
      from typing import Any
      
      Shape = TypeVarTuple("Shape")
      
      class Array(Generic[*Shape]):
          ...
      
      y: Array[*tuple[Any, ...]] = Array()
      
      def expect_variadic_array(x: Array[int, *Shape]) -> None:
          print(x)
      
      expect_variadic_array(y)
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic match with homogeneous generic variadic and other types inspection`() = test("""
      from __future__ import annotations
      
      from typing import TypeVarTuple
      from typing import Generic
      from typing import Any
      
      Shape = TypeVarTuple("Shape")
      
      class Array(Generic[*Shape]):
          ...
      
      y: Array[*tuple[Any, ...], int, str] = Array()
      
      def expect_variadic_array(x: Array[int, *Shape]) -> None:
          print(x)
      
      expect_variadic_array(y)
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic check type aliases missing parameter`() = test("""
      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import NewType
      
      Shape = TypeVarTuple("Shape")
      Height = NewType("Height", int)
      Width = NewType("Width", int)
      DType = TypeVar("DType")
      
      
      class Array(Generic[DType, *Shape]):
          ...
      
      
      Float32Array = Array[float, *Shape]
      
      
      def takes_float_array_of_specific_shape(arr: Float32Array[Height, Width]): ...
      
      
      y: Float32Array[Height] = Array()
      takes_float_array_of_specific_shape(y) # WARNING Expected type 'Array[float | int, Height, Width]', got 'Array[float | int, Height]' instead
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic check type aliases redundant parameter`() = test("""
      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import NewType
      
      Shape = TypeVarTuple("Shape")
      Height = NewType("Height", int)
      Width = NewType("Width", int)
      DType = TypeVar("DType")
      
      
      class Array(Generic[DType, *Shape]):
          ...
      
      
      Float32Array = Array[float, *Shape]
      
      
      def takes_float_array_of_specific_shape(arr: Float32Array[Height]): ...
      
      
      y: Float32Array[Height, Width] = Array()
      takes_float_array_of_specific_shape(y) # WARNING Expected type 'Array[float | int, Height]', got 'Array[float | int, Height, Width]' instead
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic empty`() = test("""
      from typing import TypeVarTuple
      
      Ts = TypeVarTuple("Ts")
      
      IntTuple = tuple[int, *Ts]
      
      c: IntTuple[()] = (1, "") # WARNING Expected type 'tuple[int]', got 'tuple[Literal[1], Literal[""]]' instead
      """)

    @Test
    fun `version dependent TypeVarTuple initialization`() = test("""
      import sys
      
      if sys.version_info >= (3, 11):
          from typing import TypeVarTuple
      else:
          from typing_extensions import TypeVarTuple
      
      PosArgsT = TypeVarTuple("PosArgsT")
      """)

    @Test
    fun `TypeVarTuple widening`() = test("""
      from typing import Literal, Sequence

      def foo[*Ts](*args: tuple[*Ts]): ...

      # nested tuples
      foo(((0,),), ((1,),))
      #            ^^^^^^^ WARNING Expected type 'tuple[tuple[Literal[0]]]' (matched generic type 'tuple[*Ts]'), got 'tuple[tuple[Literal[1]]]' instead
      def main(ones: Sequence[Literal[1]], twos: Sequence[Literal[2]]):
          # should this widen to `Sequence[int]` or should it show an error?
          foo((ones,), (twos,))
      #                ^^^^^^^ WARNING Expected type 'tuple[Sequence[Literal[1]]]' (matched generic type 'tuple[*Ts]'), got 'tuple[Sequence[Literal[2]]]' instead
      """)
  }
}

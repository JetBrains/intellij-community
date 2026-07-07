// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components
import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for tuples and unpacking: heterogeneous/homogeneous tuple inference,
 * tuple slicing, concatenation and multiplication, destructuring/unpacking (incl. nested,
 * square-bracket, for-loop and comprehension targets), tuples produced from other collections,
 * generic iterable unpacking, and variadic `*tuple` unpacking that is tuple-shaped.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyTupleTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class TupleLiteralsAndElementInference {
    @Test
    fun `heterogeneous tuple literal`() = test("""
      expr = ('1', 1, 1)
      # └ TYPE tuple[Literal['1'], Literal[1], Literal[1]]
      """)

    @Test
    fun `large heterogeneous tuple literal`() = test("""
      expr = ('1', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
      #└ TYPE tuple[Literal['1'], Literal[1], Literal[1], Literal[1], Literal[1], Literal[1], Literal[1], Literal[1], Literal[1], Literal[1], Literal[1]]
      """)

    @Test
    @TestFor(issues = ["PY-57621"])
    fun `single element tuple literal`() = test("""
      expr = (1,)
      #└ TYPE tuple[Literal[1]]
      """)

    @Test
    fun `tuple element by literal index`() = test("""
      t = ('a', 2)
      expr = t[0]
      # └ TYPE Literal['a']
      """)

    @Test
    fun `tuple element by literal index parameter`() = test("""
      from typing import List, Literal
      def foo(t: tuple[int, str, List[bool]], i: Literal[2]):
          expr = t[i]
      #   └ TYPE list[bool]
      """)

    @Test
    fun `tuple element by literal index union`() = test("""
      from typing import List, Literal
      def foo(t: tuple[int, str, List[bool]], i: Literal[0, -1]):
          expr = t[i]
      #   └ TYPE int | list[bool]
      """)

    @Test
    @TestFor(issues = ["PY-64474"])
    fun `tuple element accessed with negative index`() = test("""
      xs = (1, True, "foo")
      expr = xs[-2]
      #└ TYPE Literal[True]
      """)

    @Test
    @TestFor(issues = ["PY-64474"])
    fun `tuple element accessed with out of bound index`() = test("""
      xs = (1, True, "foo")
      expr = xs[-10], xs[10]
      #│        │        ^^ WARNING Tuple index out of range
      #│        ^^^ WARNING Tuple index out of range
      #└ TYPE tuple[Unknown, Unknown]
      """)

    @Test
    fun `homogeneous tuple element accessed with out of bound index`() = test("""
      xs: tuple[str, ...] = tuple(['foo'])
      expr = xs[-10], xs[10]
      #└ TYPE tuple[str, str]
      """)
  }

  @Nested
  inner class UnionOfTuples {
    @Test
    fun `union of tuples`() = test("""
      def x(b):
        if b:
          return (1, 'a')
        else:
          return ('a', 1)
      expr = x() # WARNING Parameter 'b' unfilled
      #└ TYPE tuple[Literal[1], Literal['a']] | tuple[Literal['a'], Literal[1]]
      """)
  }

  @Nested
  inner class BuiltinTupleAnnotations {
    @Test
    fun `bare typing Tuple annotation`() = test("""
      from typing import Tuple

      def f(expr: Tuple):
      #          └ TYPE tuple
          pass
      """)

    @Test
    fun `parametrized typing Tuple annotation`() = test("""
      from typing import Tuple

      def f(expr: Tuple[int, str]):
      #          └ TYPE tuple[int, str]
          pass
      """)

    @Test
    fun `homogeneous tuple annotation`() = test("""
      from typing import Tuple

      def f(xs: Tuple[int, ...]):
          expr = xs
      #   └ TYPE tuple[int, ...]
      """)

    @Test
    @TestFor(issues = ["PY-18762"])
    fun `homogeneous tuple from function type comment`() = test("""
      from typing import Tuple

      def f(xs):
          # type: (Tuple[int, ...]) -> None
          expr = xs
      #   └ TYPE tuple[int, ...]
      """)
  }

  @Nested
  inner class TupleSlicing {
    @Test
    fun `tuple slice type`() = test("""
      l = (1, 2, 3); expr = l[0:1]
      #              └ TYPE tuple
      """)

    @Test
    @TestFor(issues = ["PY-18560"])
    fun `custom slice type`() = test("""
      class RectangleFactory(object):
          def __getitem__(self, item):
              return 1
      factory = RectangleFactory()
      expr = factory[:]
      # └ TYPE Literal[1]
      """)

    @Test
    @TestFor(issues = ["PY-33651"])
    fun `slicing homogeneous tuple`() = test("""
      from typing import Tuple
      x: Tuple[int, ...]
      expr = x[0:]
      #└ TYPE tuple[int, ...]
      """)
  }

  @Nested
  inner class TupleFromOtherCollections {
    @Test
    fun `tuple from tuple`() = test("""
      expr = tuple(('1', 2, 3))
      #└ TYPE tuple[Literal['1'], Literal[2], Literal[3]]
      """)

    @Test
    @TestFor(issues = ["PY-19826"])
    fun `list from tuple`() = test("""
      expr = list(('1', 2, 3))
      #└ TYPE list[str | int]
      """)

    @Test
    fun `dict from tuple`() = test("""
      expr = dict((('1', 1), (2, 2), (3, '3')))
      #└ TYPE dict[str | int, int | str]
      """)

    @Test
    fun `set from tuple`() = test("""
      expr = set(('1', 2, 3))
      #└ TYPE set[str | int]
      """)

    @Test
    fun `tuple from list`() = test("""
      expr = tuple(['1', 2, 3])
      #└ TYPE tuple[str | int, ...]
      """)

    @Test
    fun `tuple from dict`() = test("""
      expr = tuple({'1': 'a', 2: 'b', 3: 4})
      # └ TYPE tuple[str | int, ...]
      """)

    @Test
    fun `tuple from set`() = test("""
      expr = tuple({'1', 2, 3})
      #└ TYPE tuple[str | int, ...]
      """)
  }

  @Nested
  inner class Iteration {
    @Test
    fun `tuple iteration type`() = test("""
      xs = (1, 'a')
      for expr in xs:
      #   └ TYPE Literal[1, 'a']
          pass
      """)

    @Test
    @TestFor(issues = ["PY-18762"])
    fun `homogeneous tuple iteration type`() = test("""
      from typing import Tuple

      xs = unknown() # type: Tuple[int, ...]
      #    ^^^^^^^ ERROR Unresolved reference 'unknown'

      for x in xs:
          expr = x
      #   └ TYPE int
      """)
  }

  @Nested
  inner class ConcatenationAndMultiplication {
    @Test
    @TestFor(issues = ["PY-12801"])
    fun `tuple concatenation`() = test("""
      expr = (1,) + (True, 'spam') + ()
      #└ TYPE tuple[Literal[1], Literal[True], Literal['spam']]
      """)

    @Test
    fun `tuple multiplication`() = test("""
      expr = (1, False) * 2
      #└ TYPE tuple[Literal[1], Literal[False], Literal[1], Literal[False]]
      """)

    @Test
    @TestFor(issues = ["PY-18762"])
    fun `homogeneous tuple multiplication`() = test("""
      from typing import Tuple

      xs = unknown() # type: Tuple[int, ...]
      #    ^^^^^^^ ERROR Unresolved reference 'unknown'
      expr = xs * 42
      #└ TYPE tuple[int, ...]
      """)
  }

  @Nested
  inner class DestructuringAndUnpacking {
    @Test
    fun `tuple assignment type`() = test("""
      t = ('a', 2)
      (expr, q) = t
      # └ TYPE Literal['a']
      """)

    @Test
    fun `tuple destructuring`() = test("""
      _, expr = (1, 'val')
      #  └ TYPE Literal['val']
      """)

    @Test
    fun `parens tuple destructuring`() = test("""
      (_, expr) = (1, 'val')
      #   └ TYPE Literal['val']
      """)

    @Test
    @TestFor(issues = ["PY-19825"])
    fun `sub tuple destructuring`() = test("""
      (a, (_, expr)) = (1, (2,'val'))
      #       └ TYPE Literal['val']
      """)

    @Test
    @TestFor(issues = ["PY-19825"])
    fun `sub tuple indirect destructuring`() = test("""
      xs = (2,'val')
      (a, (_, expr)) = (1, xs)
      #       └ TYPE Literal['val']
      """)

    @Test
    @TestFor(issues = ["PY-10967"])
    fun `default tuple parameter member`() = test("""
      def foo(xs=(1, 2)):
        expr, foo = xs
      # └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-18762"])
    fun `homogeneous tuple unpacking target`() = test("""
      from typing import Tuple

      xs = unknown() # type: Tuple[int, ...]
      #    ^^^^^^^ ERROR Unresolved reference 'unknown'
      expr, yx = xs
      #  └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-9334"])
    fun `iterate over list of nested tuples`() = test("""
      def f():
          for i, (expr, v) in [(0, ('foo', []))]:
      #           └ TYPE str
              print(expr)
      """)

    @Test
    @TestFor(issues = ["PY-38928"])
    fun `iterate list of tuples`() = test("""
      for ((_, expr)) in [(1, 'foo')]:
      #         └ TYPE str
          pass
      """)
  }

  @Nested
  inner class GenericIterableUnpacking {
    @Test
    @TestFor(issues = ["PY-29489"])
    fun `generic iterable unpacking no brackets`() = test("""
      _, expr, _ = [1, 2, 3]
      #  └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-29489"])
    fun `generic iterable unpacking parentheses`() = test("""
      (_, expr, _) = [1, 2, 3]
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-29489"])
    fun `generic iterable unpacking square brackets`() = test("""
      [_, expr] = [1, 2, 3]
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-29489"])
    fun `non generic iterable unpacking`() = test("""
      _, expr = "ab"
      #  └ TYPE LiteralString
      """)

    @Test
    fun `unpacking to nested targets in square brackets in assignments`() = test("""
      [_, [[expr], _]] = "foo", ((42,), "bar")
      #     └ TYPE Literal[42]
      """)

    @Test
    fun `unpacking to nested targets in square brackets in for loops`() = test("""
      xs = [(1, ("foo",))]
      for [_, [expr]] in xs:
      #        └ TYPE str
          pass
      """)

    @Test
    fun `unpacking to nested targets in square brackets in comprehensions`() = test("""
      xs = [(1, ("foo",))]
      ys = [expr for [_, [expr]] in xs]
      #     └ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-89977"])
    fun `homogeneous iterable unpacking in for loop`() = test("""
      def f(a: list[list[int]]):
          for expr, e in a:
      #       └ TYPE int
              pass
      """)

    @Test
    @TestFor(issues = ["PY-89977"])
    fun `homogeneous iterable unpacking in for loop square brackets`() = test("""
      def f(a: list[list[int]]):
          for [b, expr] in a:
      #           └ TYPE int
              pass
      """)

    @Test
    @TestFor(issues = ["PY-89977"])
    fun `homogeneous iterable unpacking in comprehension`() = test("""
      def f(a: list[list[int]]):
          ys = [expr for expr, e in a]
      #         └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-89977"])
    fun `homogeneous iterable star target in for loop`() = test("""
      def f(a: list[list[int]]):
          for head, *expr in a:
      #               └ TYPE list[int]
              pass
      """)

    @Test
    @TestFor(issues = ["PY-89977"])
    fun `homogeneous iterable star target in assignment`() = test("""
      def f(xs: list[int]):
          h, *expr = xs
      #       └ TYPE list[int]
      """)

    @Test
    @TestFor(issues = ["PY-89977"])
    fun `homogeneous iterable star target in the middle`() = test("""
      def f(xs: list[int]):
          a1, *expr, a3 = xs
      #        └ TYPE list[int]
      """)

    @Test
    @TestFor(issues = ["PY-89977"])
    fun `homogeneous iterable head beside star target`() = test("""
      def f(a: list[list[int]]):
          for expr, *tail in a:
      #         └ TYPE int
              pass
      """)

    @Test
    @TestFor(issues = ["PY-89977"])
    fun `nested homogeneous iterable inside tuple assignment`() = test("""
      def f():
          a, (expr, c) = (1, [2, 3])
      #          └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-89977"])
    fun `nested homogeneous iterable inside tuple for loop`() = test("""
      def f(x: list[tuple[int, list[str]]]):
          for p, (expr, r) in x:
      #              └ TYPE str
              pass
      """)

    @Test
    @TestFor(issues = ["PY-89977"])
    fun `nested star target over homogeneous iterable inside tuple for loop`() = test("""
      def f(x: list[tuple[int, list[str]]]):
          for p, [*expr] in x:
      #             └ TYPE list[str]
              pass
      """)
  }

  @Nested
  inner class NestedListUnpacking {
    @Test
    @TestFor(issues = ["PY-86873"])
    fun `nested list unpacking inner element`() = test("""
      def f(edges: list[list[int]]):
                       [[node_a], second_edge] = edges
                       expr = node_a
      #                       └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-86873"])
    fun `nested list unpacking outer element`() = test("""
      def f(edges: list[list[int]]):
                       [[node_a], second_edge] = edges
                       expr = second_edge
      #                       └ TYPE list[int]
      """)

    @Test
    @TestFor(issues = ["PY-86873"])
    fun `nested list unpacking inner element on the right`() = test("""
      def f(edges: list[list[int]]):
                       [edge, [node_b]] = edges
                       expr = node_b
      #                       └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-86873"])
    fun `nested list unpacking outer element on the left`() = test("""
      def f(edges: list[list[int]]):
                       [edge, [node_b]] = edges
                       expr = edge
      #                       └ TYPE list[int]
      """)

    @Test
    @TestFor(issues = ["PY-86873"])
    fun `nested list unpacking middle element`() = test("""
      def f(edges: list[list[int]]):
                       [edge, [node_b], edge_2] = edges
                       expr = node_b
      #                       └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-86873"])
    fun `nested list unpacking all elements into tuple`() = test("""
      def f(edges: list[list[int]]):
                       [[node_a], [node_b], [node_c]] = edges
                       expr = (node_a, node_b, node_c)
      #                       └ TYPE tuple[int, int, int]
      """)

    @Test
    @TestFor(issues = ["PY-86873"])
    fun `nested list depth 3 unpacking element`() = test("""
      def f(edges: list[list[list[int]]]):
                       [edge, [node_a]] = edges
                       expr = node_a
      #                       └ TYPE list[int]
      """)

    @Test
    @TestFor(issues = ["PY-86873"])
    fun `nested list depth 3 unpacking deep element`() = test("""
      def f(edges: list[list[list[int]]]):
                       [edge, [edge_2, [node_a]]] = edges
                       expr = node_a
      #                       └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-86873"])
    fun `nested list depth 3 unpacking middle element`() = test("""
      def f(edges: list[list[list[int]]]):
                       [edge, [edge_2, [node_a]]] = edges
                       expr = edge_2
      #                       └ TYPE list[int]
      """)

    @Test
    @TestFor(issues = ["PY-86873"])
    fun `nested list depth 3 unpacking outer element`() = test("""
      def f(edges: list[list[list[int]]]):
                       [edge, [edge_2, [node_a]]] = edges
                       expr = edge
      #                       └ TYPE list[list[int]]
      """)

    @Test
    @TestFor(issues = ["PY-86873"])
    fun `nested list unpacking targets are checked`() = test("""
      def f(edges: list[list[int]]):
          [[node_a], second_edge] = edges
          a: int = node_a
          c: list[int] = second_edge
      """)
  }

  @Nested
  inner class TupleWideningAndLiteralPreservation {
    @Test
    @TestFor(issues = ["PY-57621"])
    fun `tuple in list widens`() = test("""
      t = (1, 'hello')
      expr = [t]
      #└ TYPE list[tuple[int, str]]
      """)

    @Test
    @TestFor(issues = ["PY-57621"])
    fun `tuple in tuple is literal`() = test("""
      t = (1, 'hello')
      expr = (t, t)
      #└ TYPE tuple[tuple[Literal[1], Literal['hello']], tuple[Literal[1], Literal['hello']]]
      """)

    @Test
    @TestFor(issues = ["PY-57621"])
    fun `tuple in generic widens`() = test("""
      def f[T](t: T) -> list[T]: ...
      expr = f((1, "hello"))
      #└ TYPE list[tuple[int, str]]
      """)

    @Test
    @TestFor(issues = ["PY-57621"])
    fun `tuple as generic in tuple narrows`() = test("""
      def f[T](t: T) -> tuple[list[T], T] | T: ...
      expr = f((1, 'hello'))
      #└ TYPE tuple[list[tuple[int, str]], tuple[Literal[1], Literal['hello']]] | tuple[Literal[1], Literal['hello']]
      """)

    @Test
    @TestFor(issues = ["PY-57621"])
    fun `tuple as bare type variable is literal`() = test("""
      def f[T](t: T) -> T: ...
      expr = f((1, "hello"))
      #└ TYPE tuple[Literal[1], Literal["hello"]]
      """)
  }

  @Nested
  inner class TupleAssignabilityInspections {
    @Test
    fun `homogeneous tuple assignability`() = test("""
      from typing import Tuple


      def expects_many_ints(xs: Tuple[int, ...]):
          pass


      int_and_bool = (42, True)
      expects_many_ints(int_and_bool)

      int_and_str = (42, 'foo')
      expects_many_ints(int_and_str) # WARNING Expected type 'tuple[int, ...]', got 'tuple[Literal[42], Literal['foo']]' instead

      booleans = (True, False)  # type: Tuple[bool, ...]
      expects_many_ints(booleans)

      strings = ('foo', 'bar')  # type: Tuple[str, ...]
      expects_many_ints(strings) # WARNING Expected type 'tuple[int, ...]', got 'tuple[str, ...]' instead


      def expects_two_ints(xs: Tuple[int, int]):
          pass


      ints = (1, 2)  # type: Tuple[int, ...]
      expects_two_ints(ints) # WARNING Expected type 'tuple[int, int]', got 'tuple[int, ...]' instead
      """)

    @Test
    @TestFor(issues = ["PY-9924"])
    fun `tuple get item with slice`() = test("""
      t = (1, 2, 3, 4)
      s = slice(0, 2)
      y = t[s]
      """)

    @Test
    @TestFor(issues = ["PY-79129"])
    fun `tuple index out of range`() = test("""
      from typing import Literal

      def foo(t: tuple[int, str], i: Literal[1], j: Literal[3], k: Literal[-3]):
          t[i]
          t[-1]
          t[j] # WARNING Tuple index out of range
          t[2] # WARNING Tuple index out of range
          t[k] # WARNING Tuple index out of range
          t[-4] # WARNING Tuple index out of range

      def bar(t: tuple[int, ...]):
          t[10]
      """)

    @Test
    fun `tuple types are covariant on assignment`() = test("""
      def func(p1: tuple[int, int], p2: tuple[float, complex]):
          t1: tuple[float, complex] = p1
          t2: tuple[int, int] = p2 # WARNING Expected type 'tuple[int, int]', got 'tuple[float | int, complex | float | int]' instead
      """)

    @Test
    fun `tuple Any is bidirectionally compatible with any tuple`() = test("""
      from typing import Any
      def func(p1: tuple[Any], p2: tuple[float]):
          v1: tuple[Any] = p2
          v2: tuple[float] = p1
      """)

    @Test
    fun `tuple Any arbitrary length can be assigned to any tuple`() = test("""
      from typing import Any
      def func(p1: tuple[Any, ...]):
          v1: tuple[float, float] = p1
          v2: tuple[float, ...] = p1
      """)

    @Test
    fun `tuple Any arbitrary length is assignable from any tuple`() = test("""
      from typing import Any
      def func(p1: tuple[float, float]):
          v1: tuple[Any, ...] = p1
      """)

    @Test
    @TestFor(issues = ["PY-64359"])
    fun `tuple dict values`() = test(TestOptions(assertRecursionPrevention = false), """
      def f(a: dict[str, int]):
          b: tuple[int, ...] = tuple(a.values())
      """)
  }

  @Nested
  inner class VariadicTupleUnpacking {
    @Test
    fun `homogeneous unpacked tuple is assignable to homogeneous tuple`() = test("""
      def func(p1: tuple[int, *tuple[int, ...]]):
          v1: tuple[int, ...] = p1
      """)

    @Test
    fun `homogeneous unpacked tuple is not assignable to non homogeneous tuple of size 1`() = test("""
      def func(p: tuple[int, *tuple[int, ...]]):
          v: tuple[int] = p # WARNING Expected type 'tuple[int]', got 'tuple[int, *tuple[int, ...]]' instead
      """)

    @Test
    @TestFor(issues = ["PY-88727"])
    fun `fixed tuple args expansion`() = test("""
      def foo(*args: *tuple[int, str]) -> None: ...

      foo(1, "hello")
      foo("hello", 1)
      #   │        └ WARNING Expected type 'str', got 'Literal[1]' instead
      #   ^^^^^^^ WARNING Expected type 'int', got 'Literal["hello"]' instead
      """)

    @Test
    @TestFor(issues = ["PY-88727"])
    fun `fixed tuple args with variadic in the middle`() = test("""
      def foo(*args: *tuple[int, *tuple[str, ...], float]) -> None: ...

      foo(1, "a", "b", 3.14)
      foo(1, 3.14)
      foo("wrong", "a", 3.14) # WARNING Expected type 'int', got 'Literal["wrong"]' instead
      foo(1, "a", "b", "c", "d") # WARNING Expected type 'float | int', got 'Literal["d"]' instead
      """)

    @Test
    @TestFor(issues = ["PY-88727"])
    fun `fixed tuple args with variadic at start`() = test("""
      def foo(*args: *tuple[*tuple[int, ...], str, bool]) -> None: ...

      foo("a", True)
      foo(1, "a", True)
      foo(1, 2, 3, "a", True)
      foo("wrong", "a", True) # WARNING Expected type 'int', got 'Literal["wrong"]' instead
      foo(1, 2, True) # WARNING Expected type 'str', got 'Literal[2]' instead
      foo(1, "a", "wrong") # WARNING Expected type 'bool', got 'Literal["wrong"]' instead
      """)

    @Test
    @TestFor(issues = ["PY-76908"])
    fun `sequence from unpacked tuple`() = test("""
      from typing import Sequence, TypeVar
      T = TypeVar("T")
      def test_seq(x: Sequence[T]) -> Sequence[T]:
          return x
      def func(p: tuple[int, *tuple[str, ...]]):
          expr = test_seq(p)
      #   └ TYPE Sequence[int | str]
      """)

    @Test
    @TestFor(issues = ["PY-76908"])
    fun `sequence from deeply unpacked tuple`() = test("""
      from typing import Sequence, TypeVar
      T = TypeVar("T")
      def test_seq(x: Sequence[T]) -> Sequence[T]:
          return x
      def func(p: tuple[int, *tuple[complex, *tuple[str, ...]]]):
          expr = test_seq(p)
      #   └ TYPE Sequence[int | complex | float | str]
      """)

    @Test
    @TestFor(issues = ["PY-43585"])
    fun `tuple literal splicing a list`() = test("""
      def f(first: int, rest: list[int]):
          expr = (first, *rest)
      #   └ TYPE tuple[int, *tuple[int, ...]]
      """)

    @Test
    @TestFor(issues = ["PY-43585"])
    fun `tuple literal splicing a homogeneous tuple`() = test("""
      def f(first: int, rest: tuple[str, ...]):
          expr = (first, *rest)
      #   └ TYPE tuple[int, *tuple[str, ...]]
      """)
  }

  @Nested
  inner class UnpackingDiagnostics {
    @Test
    @TestFor(issues = ["PY-39258"])
    fun `nested tuple unpacking balance`() = test("""
      def func(args: tuple[str, tuple[int, int, int]]):
          s, (x, y) = args # WARNING Too many values to unpack from 'tuple[int, int, int]': expected 2, got 3

      def func2(args: tuple[str, tuple[int, int]]):
          s, (x, y, z) = args # WARNING Not enough values to unpack from 'tuple[int, int]': expected 3, got 2

      def func3(args: tuple[str, tuple[int, int]]):
          s, (x, y) = args

      def func4(args: tuple[str, tuple[int, ...]]):
          s, (x, y) = args
      """)

    @Test
    @TestFor(issues = ["PY-85232"])
    fun `for loop unpacking a non iterable item`() = test("""
      for a, b in [1]: # WARNING Expected an iterable, got 'int'
          pass

      for c in [1]:
          pass

      for x, y in [(1, 2)]:
          pass

      for k, v in [[1, 2]]:
          pass

      def f(rows: list[str]):
          for first, second in rows:
              pass
      """)

    @Test
    @TestFor(issues = ["PY-90173"])
    fun `annotated target unpacked from list assignment is type checked`() = test("""
      x: int
      x, _ = ["foo", "bar"] # WARNING Expected type 'int', got 'str' instead

      y: str
      y, _ = ["foo", "bar"]

      def f(items: list[int]):
          z: str
          z, _ = items # WARNING Expected type 'str', got 'int' instead
      """)

    @Test
    @TestFor(issues = ["PY-90173"])
    fun `annotated target unpacked in for loop is type checked`() = test("""
      x: int
      for x, _ in [("a", "b")]: # WARNING Expected type 'int', got 'str' instead
          pass

      y: int
      for y, _ in [(1, 2)]:
          pass

      def f(matrix: list[list[int]]):
          a: str
          for a, b in matrix: # WARNING Expected type 'str', got 'int' instead
              pass
      """)

    @Test
    @TestFor(issues = ["PY-39258"])
    fun `nested tuple unpacking balance in for loop`() = test("""
      def func(rows: list[tuple[str, tuple[int, int, int]]]):
          for s, (x, y) in rows: # WARNING Too many values to unpack from 'tuple[int, int, int]': expected 2, got 3
              pass

      def func2(rows: list[tuple[str, tuple[int, int]]]):
          for s, (x, y, z) in rows: # WARNING Not enough values to unpack from 'tuple[int, int]': expected 3, got 2
              pass

      def func3(rows: list[tuple[str, tuple[int, int]]]):
          for s, (x, y) in rows:
              pass

      def func4(rows: list[tuple[str, tuple[int, ...]]]):
          for s, (x, y) in rows:
              pass
      """)

    @Test
    @TestFor(issues = ["PY-27205"])
    fun `tuple literal splicing a fixed tuple matches its target type`() = test("""
      def f(t: tuple[int, int]):
          expr = (1, *t)
      """)

    @Test
    @TestFor(issues = ["PY-40735"])
    fun `unpacked tuple argument fills parameters`() = test("""
      def foo(a: str, b: int): ...

      a = "1",
      foo(*("1",), 1)
      foo(*a, 1)
      foo(*("1", 2))
      """)

    @Test
    @TestFor(issues = ["PY-40735"])
    fun `unpacked tuple variable argument overfills parameters`() = test("""
      def foo(a: str, b: int): ...

      xe = "1", 2
      foo(*xe, 3) # WARNING Unexpected argument
      """)

    @Test
    @TestFor(issues = ["PY-40735"])
    fun `unpacked tuple literal argument is type checked`() = test("""
      def foo(a: str, b: int): ...

      foo(*("1",), 1)
      foo(*("1", 2))
      foo(*("1",), "2") # WARNING Expected type 'int', got 'Literal["2"]' instead
      foo(*("1", "2")) # WARNING Expected type 'int', got 'Literal["2"]' instead
      """)

    @Test
    @TestFor(issues = ["PY-40735"])
    fun `unpacked tuple variable argument is type checked`() = test("""
      def foo(a: str, b: int): ...

      xe = "1",
      foo(*xe, 2)
      foo(*xe, "2") # WARNING Expected type 'int', got 'Literal["2"]' instead
      """)
  }

  @Test
  @TestFor(issues = ["PY-23138"])
  fun `homogeneous tuple plus heterogeneous tuple with the same elements type`() =
    test(TestOptions(assertRecursionPrevention = false), """
    A = tuple(sorted([1, 4, 2]))

    B = A + (4, 6, 7, 8)
    """)
}

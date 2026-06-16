// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for comprehensions, iteration type inference, generators and
 * asynchronous iteration/coroutines.
 */
class PyComprehensionAndIteratorTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class Comprehensions {
    @Test
    fun `set comprehension type`() = test("""
      expr = {i for i in range(3)}
      # └ TYPE set[int]
      """)

    // PY-7020
    @Test
    fun `list comprehension type`() = test(TestOptions(assertRecursionPrevention = false), """
      expr = [str(x) for x in range(10)]
      # └ TYPE list[str]
      """)

    // PY-7021
    @Test
    fun `generator comprehension type`() = test(TestOptions(assertRecursionPrevention = false), """
      expr = (str(x) for x in range(10))
      # └ TYPE Generator[str, Unknown, None]
      """)

    // PY-7021
    @Test
    fun `iterate over generator comprehension`() = test(TestOptions(assertRecursionPrevention = false), """
      xs = (str(x) for x in range(10))
      for expr in xs:
      #   └ TYPE str
          pass
      """)

    // PEP 798
    @Test
    fun `unpacking in list comprehension type`() = test("""
      def f(its: list[list[int]]):
          expr = [*it for it in its]
      #   └ TYPE list[int]
      """)

    // PEP 798
    @Test
    fun `unpacking in set comprehension type`() = test("""
      def f(its: list[list[int]]):
          expr = {*it for it in its}
      #   └ TYPE set[int]
      """)

    // PEP 798
    @Test
    fun `unpacking in generator comprehension type`() = test(TestOptions(assertRecursionPrevention = false), """
      def f(its: list[list[int]]):
          expr = (*it for it in its)
      #   └ TYPE Generator[int, Unknown, None]
      """)

    // PEP 798
    @Test
    fun `unpacking in dict comprehension type`() = test("""
      def f(dicts: list[dict[str, int]]):
          expr = {**d for d in dicts}
      #   └ TYPE dict[str, int]
      """)

    @Test
    fun `list constructor call with generator expression`() = test("""
      expr = list(int(i) for i in '1')
      # └ TYPE list[int]
      """)

    @Test
    fun `dict comprehension expression with generics`() = test("""
      from typing import Callable, Dict, Any

      def test(x: Dict[str, Callable[[Any], Any]]):
          y = {k: v for k, v in x.items()}
          expr = y
      #   └ TYPE dict[str, (Any) -> Any]
      """)

    @Test
    fun `dict comprehension from kwargs`() = test("""
      def test(**kwargs):
          expr = {k: v for k, v in kwargs.items()}
      #   └ TYPE dict[str, Unknown]
      """)

    @TestFor(issues = ["PY-27708"])
    @Test
    fun `set comprehension expression with generics`() = test("""
      from typing import Callable, Any, Set

      def test(x: Set[Callable[[Any], Any]]):
          y = {k for k in x}
          expr = y
      #   └ TYPE set[(Any) -> Any]
      """)

    @Test
    fun `csv DictReader iterator type in comprehension`() = test("""
      import csv
      with open("file.csv") as f:
          reader = csv.DictReader(f)
          expr = [line for line in reader]
      #   └ TYPE list[dict[str | Any, str | Any]]
      """)

    @TestFor(issues = ["PY-16585"])
    @Test
    fun `comment after comprehension in assignment`() = test("""
      from typing import List

      xs = [expr for expr in range(10)]  # type: List[int]
      #     └ TYPE int
      """)

    @TestFor(issues = ["PY-16585"])
    @Test
    fun `comment after comprehension in for loop`() = test(TestOptions(assertRecursionPrevention = false), """
      for _ in [str(expr) for expr in range(10)]:  # type: str
      #             └ TYPE int
          pass
      """)

    @TestFor(issues = ["PY-16585"])
    @Test
    fun `comment after comprehension in with statement`() = test("""
      def f(x): ...
      with f([expr for expr in range(10)]) as _: # type: str
      #    │  └ TYPE int
      #    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'contextlib.AbstractContextManager', got 'None' instead
          pass
      """)
  }

  @Nested
  inner class IterationTypeInference {
    @Test
    fun `iteration over list literal`() = test("""
      for expr in [1, 2, 3]: pass
      #   └ TYPE int
      """)

    @TestFor(issues = ["PY-20063"])
    @Test
    fun `iterated set element`() = test("""
      xs = {1}
      for expr in xs:
      #   └ TYPE int
          print(expr)
      """)

    @Test
    fun `tuple iteration type`() = test("""
      xs = (1, 'a')
      for expr in xs:
      #   └ TYPE Literal[1, 'a']
          pass
      """)

    @Test
    fun `iteration type from __getitem__`() = test("""
      class C(object):
          def __getitem__(self, index):
              return 0
          def __len__(self):
              return 10
      for expr in C():
      #   └ TYPE Literal[0]
          pass
      """)

    @Test
    fun `union iteration`() = test("""
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

    @TestFor(issues = ["PY-20794"])
    @Test
    fun `iterate over pure list`() = test("""
      l = None  # type: list
      #   ^^^^ WARNING Expected type 'list[Unknown]', got 'None' instead
      for expr in l:
      #   └ TYPE Unknown
          print(expr)
      """)

    @TestFor(issues = ["PY-20794"])
    @Test
    fun `iterate over dict value with default value`() = test("""
      d = None  # type: dict
      #   ^^^^ WARNING Expected type 'dict[Unknown, Unknown]', got 'None' instead
      for expr in d.get('field', []):
      #   └ TYPE Unknown
          print(expr['id'])
      """)

    @TestFor(issues = ["PY-22181"])
    @Test
    fun `iteration over iterable with separate iterator using next`() =
      test(TestOptions(languageLevel = LanguageLevel.PYTHON27, assertRecursionPrevention = false), """
      class AIter(object):
          def next(self):
              return 5
      class A(object):
          def __iter__(self):
              return AIter()
      a = A()
      for expr in a:
      #   └ TYPE Literal[5]
          print(expr)
      """)

    @TestFor(issues = ["PY-22181"])
    @Test
    fun `iteration over iterable with separate iterator using dunder next`() = test("""
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
      """)

    @Test
    fun `iter result on iterable`() = test("""
      from typing import Iterable

      xs: Iterable[int]
      expr = iter(xs)
      # └ TYPE Iterator[int]
      """)

    @Test
    fun `next result on iterator`() = test("""
      from typing import Iterable

      xs: Iterable[int]
      expr = iter(xs).__next__()
      # └ TYPE int
      """)

    @Test
    fun `iter on list of lists result`() = test("""
      expr = iter([[1, 2, 3]])
      # └ TYPE Iterator[list[Literal[1, 2, 3]]]
      """)

    @Test
    fun `iterable for loop`() = test("""
      from typing import Iterable

      def foo() -> Iterable[int]:
          pass

      for expr in foo():
      #   └ TYPE int
          pass
      """)

    @Test
    fun `homogeneous tuple iteration type`() = test("""
      from typing import Tuple

      xs = unknown() # type: Tuple[int, ...]
      #    ^^^^^^^ ERROR Unresolved reference 'unknown'

      for x in xs:
          expr = x
      #   └ TYPE int
      """)

    @Test
    fun `iteration over regular str emits str not literal string`() = test("""
      s = "foo"
      for expr in s:
      #   └ TYPE LiteralString
          pass
      """)

    @TestFor(issues = ["PY-64481"])
    @Test
    fun `for loop target type comes from correct dunder iter overload`() = test("""
      from typing import overload

      class Super:
          @overload
          def __iter__(self: 'Sub') -> list['Sub']: ...
      #       ^^^^^^^^ WARNING A series of @overload-decorated methods should always be followed by an implementation that is not @overload-ed
          @overload
          def __iter__(self) -> list['Super']: ...

      class Sub(Super): ...

      for expr in Super():
      #   └ TYPE Super
          pass
      """)

    @Test
    fun `generic iterator parameterized with another generic`() = test("""
      from typing import Iterator, Generic, TypeVar

      T = TypeVar("T")

      class Entry(Generic[T]):
          pass

      class MyIterator(Iterator[Entry[T]]):
          def __next__(self) -> Entry[T]: ...

      def iter_entries(path: T) -> MyIterator[T]: ...

      def main() -> None:
          for x in iter_entries("some path"):
              expr = x
      #       └ TYPE Entry[str]
      """)

    @Test
    fun `dunder iter defined in metaclass`() = test("""
      from collections.abc import Iterator

      class MyIterMeta(type):
          def __iter__(self) -> Iterator[int]: ...

      class MyClass(metaclass=MyIterMeta): ...

      expr = set(MyClass)
      # └ TYPE set[int]
      """)

    @TestFor(issues = ["PY-87575"])
    @Test
    fun `dunder iter defined in metaclass has higher priority than inherited class`() = test("""
      from collections.abc import Iterator

      class MyIterMeta(type):
          def __iter__(self) -> Iterator[int]: ...

      class IterBase:
          def __iter__(self) -> Iterator[str]: ...

      class MyClass(IterBase, metaclass=MyIterMeta): ...

      expr = set(MyClass)
      # └ TYPE set[int]
      """)

    @TestFor(issues = ["PY-87575"])
    @Test
    fun `dunder iter defined in metaclass has higher priority than inherited builtin str`() = test("""
      from collections.abc import Iterator

      class MyIterMeta(type):
          def __iter__(self) -> Iterator[int]: ...

      # even though str inherits Iterable[str], MyIterMeta.__iter__ will be called in runtime and has higher priority
      class MyClass(str, metaclass=MyIterMeta): ...

      expr = set(MyClass)
      # └ TYPE set[int]
      """)

    @TestFor(issues = ["PY-20832"])
    @Test
    fun `structural type with dunder iter`() = test("""
      def expand(values1):
          for a in values1:
              print(a)
          expr = values1
      #   └ TYPE {__iter__}
      """)

    @TestFor(issues = ["PY-16125"])
    @Test
    fun `typing iterable for loop assignability`() = test("""
      from typing import Iterable, Iterator, Sequence, List, Mapping, Dict


      def f1() -> Iterable[int]:
          pass


      def f2() -> Iterator[int]:
          pass


      def f3() -> Sequence[int]:
          pass


      def f4() -> List[int]:
          pass


      def f5() -> Mapping[str, int]:
          pass


      def f6() -> Dict[str, int]:
          pass


      for x in f1():
          pass

      for x in f2():
          pass

      for x in f3():
          pass

      for x in f4():
          pass

      for x in f5():
          pass

      for x in f6():
          pass
      """)

    @TestFor(issues = ["PY-85997"])
    @Test
    fun `recursive iterator protocol assignability`() = test(
      // It simulates how the `builtins.map` type is declared using Self.
      TestOptions(assertRecursionPrevention = false),
      """
      from typing import Iterator, Self

      class MyIterable[T]:
          def __next__(self) -> T: ...
          def __iter__(self) -> Self: ...

      ys: MyIterable[str]
      xs: Iterator[str] = ys
      """)

    @Test
    fun `builtin map type is iterator`() = test(
      TestOptions(assertRecursionPrevention = false),
      """
      from typing import Iterator


      def foo() -> Iterator[str]:
          return map(str, range(5))
      """)

    @Test
    fun `dict items and iterable matches`() = test("""
      from typing import Iterable, Tuple

      def foo(bar: Iterable[Tuple[str, int]]):
          pass

      if __name__ == '__main__':
          bar_dict = {'abc': 42}
          foo(bar_dict.items())
      """)

    @TestFor(issues = ["PY-38897"])
    @Test
    fun `dict items and iterable matches generic`() = test("""
      from typing import Tuple

      def make_dict() -> dict[int, str]:
          ...

      def key_func(param: Tuple[int, str]) -> int:
          ...

      def foo() -> None:
          my_dict = make_dict()
          items = my_dict.items()
          print(max(items, key=key_func))
      """)

    @Test
    fun `list literal passed to iter`() = test("iter([1, 2, 3])")

    @TestFor(issues = ["PY-52648"])
    @Test
    fun `list literal passed to iter simplified`() = test("""
      from typing import Generic, Protocol, TypeVar

      T = TypeVar('T', covariant=True)
      T2 = TypeVar('T2')
      T3 = TypeVar('T3')


      class SupportsIter(Protocol[T]):
          def __iter__(self) -> T:
              pass


      def my_iter(x: SupportsIter[T2]) -> T2:
          pass


      class MyList(Generic[T3]):
          def __init__(self, x: T3):
              pass

          def __iter__(self) -> list[int]:
              pass


      x = MyList('foo')
      my_iter(x)
      """)

    @Test
    fun `map return type for-loop element`() = test(TestOptions(assertRecursionPrevention = false), """
      for x in map(lambda x: 42, 'foo'):
          expr = x
      #   └ TYPE int
      """)

    @Test
    fun `enumerate type`() = test("""
      a: list[int] = [1, 2, 3]
      for expr in enumerate(a):
      #   └ TYPE tuple[int, int]
          pass
      """)

    @Test
    fun `pathlib iterdir`() = test("""
      import pathlib
      expr = pathlib.Path("").iterdir()
      # └ TYPE Generator[Path, None, None]
      """)
  }

  @Nested
  inner class GeneratorsAndYield {
    // PY-5831
    @Test
    fun `yield expression type`() = test("""
      def f():
          expr = yield 2
      #   └ TYPE Unknown
      """)

    // PY-9590
    @Test
    fun `parenthesized yield expression type`() = test("""
      def f():
          expr = (yield 2)
      #   └ TYPE Unknown
      """)

    // PY-7215
    @Test
    fun `function with nested generator`() = test("""
      def f():
          def g():
              yield 10
          return list(g())

      expr = f()
      # └ TYPE list[int]
      """)

    @Test
    fun `generator function type`() = test("""
      def f():
          yield 'foo'
          return 0

      expr = f()
      # └ TYPE Generator[Literal["foo"], Unknown, Literal[0]]
      """)

    @TestFor(issues = ["PY-26643"])
    @Test
    fun `replace self in generator`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON34),
      """
      class A:
          def foo(self):
              yield self
              return self
      class B(A):
          pass
      expr = B().foo()
      # └ TYPE Generator[B, Unknown, B]
      """)

    @Test
    fun `yield inside lambda does not make enclosing function a generator`() = test("""
      def foo():
          y = lambda x: (yield x)
          return 42
      expr = foo()
      # └ TYPE Literal[42]
      """)

    @TestFor(issues = ["PY-20710"])
    @Test
    fun `lambda generator`() = test("""
      expr = (lambda: (yield 1))()
      # └ TYPE Generator[Literal[1], Unknown, Unknown]
      """)

    @TestFor(issues = ["PY-20710"])
    @Test
    fun `generator delegating to lambda generator`() = test("""
      def g():
          yield from (lambda: (yield 1))()
          return "foo"
      expr = g()
      # └ TYPE Generator[Literal[1], Unknown, Literal["foo"]]
      """)

    @TestFor(issues = ["PY-20710"])
    @Test
    fun `yield expression type from generator send type hint`() = test("""
      from typing import Generator

      def g() -> Generator[str, int, None]:
          expr = yield "foo"
      #   └ TYPE int
      """)

    @TestFor(issues = ["PY-20710"])
    @Test
    fun `yield from expression type from generator return type hint`() = test("""
      from typing import Generator, Any

      def delegate() -> Generator[None, Any, int]:
          yield
          return 42

      def g():
          expr = yield from delegate()
      #   └ TYPE int
      """)

    @TestFor(issues = ["PY-20710"])
    @Test
    fun `yield from lambda`() = test("""
      from typing import Generator

      def gen1() -> Generator[int, str, bool]:
          yield 42
          return True

      def gen2():
          yield "str"
          return True

      l = lambda: (yield from gen1()) or (yield from gen2())
      expr = l()
      # └ TYPE Generator[int | Literal["str"], str | Unknown, bool | Literal[True]]
      """)

    // PY-6702
    @Test
    fun `yield from type`() = test("""
      def subgen():
          for i in [1, 2, 3]:
              yield i

      def gen():
          yield 'foo'
          yield from subgen()
          yield 3.14

      for expr in gen():
      #   └ TYPE Literal["foo"] | int | float
          pass
      """)

    @TestFor(issues = ["PY-12944"])
    @Test
    fun `yield from return type when delegate is plain list`() = test("""
      def a():
          yield 1
          return 'a'

      y = [1, 2, 3]

      def b():
          expr = yield from y
      #   └ TYPE None
          return expr
      """)

    @TestFor(issues = ["PY-12944"])
    @Test
    fun `yield from return type from delegate generator return`() = test("""
      def a():
          yield 1
          return 'a'

      def b():
          expr = yield from a()
      #   └ TYPE Literal["a"]
          return expr
      """)

    @TestFor(issues = ["PY-12944"])
    @Test
    fun `yield from yields delegate element type`() = test("""
      def g():
          yield 1
          return 'abc'

      def f():
          x = yield from g()

      for expr in f():
      #   └ TYPE Literal[1]
          pass
      """)

    @Test
    fun `yield from homogeneous tuple`() = test("""
      import typing
      def get_tuple() -> typing.Tuple[str, ...]:
          pass
      def gen():
          yield from get_tuple()
      for expr in gen():    pass
      #   └ TYPE str
      """)

    @Test
    fun `yield from heterogeneous tuple`() = test("""
      import typing
      def get_tuple() -> typing.Tuple[int, int, str]:
          pass
      def gen():
          yield from get_tuple()
      for expr in gen():    pass
      #   └ TYPE int | str
      """)

    @Test
    fun `yield from unknown tuple`() = test("""
      def get_tuple() -> tuple:
          pass
      def gen():
          yield from get_tuple()
      for expr in gen():    pass
      #   └ TYPE Unknown
      """)

    @Test
    fun `yield from unknown list`() = test("""
      def get_list() -> list:
          pass
      def gen():
          yield from get_list()
      for expr in gen():    pass
      #   └ TYPE Unknown
      """)

    @Test
    fun `yield from unknown dict`() = test("""
      def get_dict() -> dict:
          pass
      def gen():
          yield from get_dict()
      for expr in gen():    pass
      #   └ TYPE Unknown
      """)

    @Test
    fun `yield from unknown set`() = test("""
      def get_set() -> set:
          pass
      def gen():
          yield from get_set()
      for expr in gen():    pass
      #   └ TYPE Unknown
      """)

    @TestFor(issues = ["PY-78044"])
    @Test
    fun `generator yields self`() = test("""
      from collections.abc import Generator
      from typing import Self

      class A:
          @classmethod
          def f(cls) -> Generator[Self, None, None]:
              pass

      for x in A.f():
          expr = x
      #   └ TYPE A
      """)

    @TestFor(issues = ["PY-78044"])
    @Test
    fun `generator yields self nested`() = test("""
      from collections.abc import Generator
      from typing import Self

      class A:
          @classmethod
          def f(cls) -> Generator[Self, None, None]:
              pass

      class B(A): ...
      class C(B): ...

      for x in C.f():
          expr = x
      #   └ TYPE C
      """)

    // PY-6729
    @Test
    fun `yield from non-iterable`() = test("""
      class CustomIterable:
          def __iter__(self):
              return self

          def __next__(self):
              return 42


      class CustomIterable2(CustomIterable):
          pass


      class NonIterable:
          pass


      def g():
          yield from CustomIterable()
          yield from CustomIterable2()
          yield from NonIterable() # WARNING Expected type 'collections.Iterable', got 'NonIterable' instead
          yield from [42]
          yield from "hello"
          yield from object() # WARNING Expected type 'collections.Iterable', got 'object' instead
      """)

    @TestFor(issues = ["PY-12944"])
    @Test
    fun `delegated generator yield from value type checked`() = test("""
      def a():
          yield 1
          return 'a'


      def f(x: int) -> int:
          return x


      def test():
          return f((yield from a())) # WARNING Expected type 'int', got 'Literal["a"]' instead
      """)

    @Test
    fun `function yield type`() = test("""
      from typing import Any, Generator, Iterable, Iterator, AsyncIterable, AsyncIterator, AsyncGenerator, Protocol

      # Fix incorrect YieldType
      def a() -> Iterable[str]:
          yield 42 # WARNING Expected yield type 'str', got 'Literal[42]' instead

      def b() -> Iterator[str]:
          yield 42 # WARNING Expected yield type 'str', got 'Literal[42]' instead

      def c() -> Generator[str, Any, int]:
          yield 13 # WARNING Expected yield type 'str', got 'Literal[13]' instead
          return 42

      def c2() -> Generator[int, Any, str]:
          yield 13
          return 42 # WARNING Expected type 'str', got 'Literal[42]' instead

      # Suggest AsyncGenerator
      async def d() -> Iterable[int]: # WARNING Expected type 'AsyncGenerator[Literal[42], Unknown]', got 'Iterable[int]' instead
          yield 42

      async def e() -> Iterator[int]: # WARNING Expected type 'AsyncGenerator[Literal[42], Unknown]', got 'Iterator[int]' instead
          yield 42

      async def f() -> Generator[int, str, None]: # WARNING Expected type 'AsyncGenerator[Literal[13], str]', got 'Generator[int, str, None]' instead
          yield 13

      # Suggest sync Generator
      def g() -> AsyncIterable[int]: # WARNING Expected type 'Generator[Literal[42], Unknown, None]', got 'AsyncIterable[int]' instead
          yield 42

      def h() -> AsyncIterator[int]: # WARNING Expected type 'Generator[Literal[42], Unknown, None]', got 'AsyncIterator[int]' instead
          yield 42

      def i() -> AsyncGenerator[int, str]: # WARNING Expected type 'Generator[Literal[13], str, None]', got 'AsyncGenerator[int, str]' instead
          yield 13

      def j() -> Iterator[int]:
          yield from j()

      def k() -> Iterator[str]:
          yield from j() # WARNING Expected yield type 'str', got 'int' instead
          yield from [1] # WARNING Expected yield type 'str', got 'int' instead

      def l() -> Generator[None, int, None]:
          x: float = yield

      def m() -> Generator[None, float, None]:
          yield from l() # WARNING Expected send type 'float | int', got 'int' instead

      def n() -> Generator[None, float, None]:
        x: float = yield

      def o() -> Generator[None, int, None]:
          yield from n()

      def p() -> Generator[int, None, None]:
          yield from [1, 2]
          yield from [3, 4]

      def q() -> int:
          x = lambda: (yield "str")
          return 42

      async def r() -> AsyncGenerator[int]:
          yield 42

      def s() -> Generator[int]:
          yield from r() # WARNING Cannot yield from 'AsyncGenerator[int, None]', use 'async for' instead

      def t() -> object: # no error here
          yield None # no error here

      class IntIterator(Protocol):
        def __next__(self, /) -> int:
          ...

      def x(b: bool) -> IntIterator:
        if b:
            yield 0
        yield "str" # WARNING Expected yield type 'int', got 'Literal["str"]' instead

      class TIterator[T](Protocol):
          def __next__(self, /) -> T:
              ...

      def y(b: bool) -> TIterator[int]:
          if b:
              yield 0
          yield "str" # WARNING Expected yield type 'int', got 'Literal["str"]' instead
      """)

    @TestFor(issues = ["PY-20709"])
    @Test
    fun `generator return type`() = test("""
      from typing import Generator

      def echo_round() -> Generator[int, float, str]:
          sent = yield 0
          while sent >= 0:
              sent = yield round(sent)
          return 'Done'
      """)

    @TestFor(issues = ["PY-20657", "PY-21916"])
    @Test
    fun `generator annotated to return iterable`() = test("""
      from typing import Iterable


      def g1() -> Iterable[int]:
          for i in range(10):
              yield i


      def g2() -> Iterable[int]:
          yield 42
          return None


      def g3() -> Iterable:
          yield 42


      def g4() -> Iterable:
          yield 42
          return None
      """)

    @Test
    @TestFor(issues = ["PY-89564"])
    fun `no subtype issue on creation expression in yield`() = test("""
      from typing import Generator

      def f() -> Generator[list[object], None, None]:
          yield [1, 2]
      """)

    @Test
    @TestFor(issues = ["PY-89564"])
    fun `no subtype issue on creation expression in yield from`() = test("""
      from typing import Generator

      def f() -> Generator[list[object], None, None]:
          yield from [[1, 2]]
      """)
  }

  @Nested
  inner class AsyncIterationAndCoroutines {
    @Test
    fun `await awaitable`() = test("""
      class C:
          def __await__(self):
              yield 'foo'
              return 0

      async def foo():
          c = C()
          expr = await c
      #   └ TYPE Literal[0]
      """)

    @Test
    fun `async def return type`() = test("""
      async def foo(x):
          await x
          return 0

      def bar(y):
          expr = foo(y)
      #   └ TYPE CoroutineType[Unknown, Unknown, Literal[0]]
      """)

    @Test
    fun `await coroutine`() = test("""
      async def foo(x):
          await x
          return 0

      async def bar(y):
          expr = await foo(y)
      #   └ TYPE Literal[0]
      """)

    @Test
    fun `coroutine return type annotation`() = test("""
      async def foo() -> int: ...

      async def bar():
          expr = await foo()
      #   └ TYPE int
      """)

    @Test
    fun `await on typing Coroutine annotation`() = test("""
      from typing import Any, Coroutine

      x: Coroutine[Any, Any, int]

      async def bar():
          expr = await x
      #   └ TYPE int
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `async generator`() = test("""
      async def asyncgen():
          yield 42
      expr = asyncgen()
      # └ TYPE AsyncGenerator[Literal[42], Unknown]
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `async generator dunder aiter`() = test("""
      async def asyncgen():
          yield 42
      expr = asyncgen().__aiter__()
      # └ TYPE AsyncIterator[Literal[42]]
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `async generator dunder anext`() = test("""
      async def asyncgen():
          yield 42
      expr = asyncgen().__anext__()
      # └ TYPE Coroutine[Any, Any, Literal[42]]
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `async generator await on dunder anext`() = test("""
      async def asyncgen():
          yield 42
      async def asyncusage():
          expr = await asyncgen().__anext__()
      #       └ TYPE () -> CoroutineType[Unknown, Unknown, None] FIXME int
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `async generator asend`() = test("""
      async def asyncgen():
          yield 42
      expr = asyncgen().asend("hello")
      #└ TYPE Coroutine[Any, Any, Literal[42]]
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `async generator await on asend`() = test("""
      async def asyncgen():
          yield 42
      async def asyncusage():
          expr = await asyncgen().asend("hello")
      #       └ TYPE () -> CoroutineType[Unknown, Unknown, None] FIXME int
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `iterated async generator element`() = test("""
      async def asyncgen():
          yield 10
      async def run():
          async for i in asyncgen():
              expr = i
      #       └ TYPE Literal[10]
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `element in async comprehensions - set`() = test("""
      async def asyncgen():
          yield 10
      async def run():
          {expr async for expr in asyncgen()}
      #    └ TYPE Literal[10]
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `element in async comprehensions - list`() = test("""
      async def asyncgen():
          yield 10
      async def run():
          [expr async for expr in asyncgen()]
      #    └ TYPE Literal[10]
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `element in async comprehensions - dict`() = test("""
      async def asyncgen():
          yield 10
      async def run():
          {expr: expr ** 2 async for expr in asyncgen()}
      #    └ TYPE Literal[10]
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `element in async comprehensions - generator`() = test("""
      async def asyncgen():
          yield 10
      async def run():
          (expr async for expr in asyncgen())
      #    └ TYPE Literal[10]
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `element in async comprehensions - list call`() = test("""
      async def asyncgen():
          yield 10
      async def run():
          list(expr async for expr in asyncgen())
      #        └ TYPE Literal[10]
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `element in async comprehensions - nested clauses on inner target`() = test("""
      def check(x): ...
      async def asyncgen():
          yield 10
      async def run():
          dataset = {data async for expr in asyncgen()
      #              └ TYPE Literal[10]
                          async for data in asyncgen()
                          if check(data)}
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `element in async comprehensions - nested clauses on outer target`() = test("""
      def check(x): ...
      async def asyncgen():
          yield 10
      async def run():
          dataset = {expr async for line in asyncgen()
      #              └ TYPE Literal[10]
                          async for expr in asyncgen()
                          if check(expr)}
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `await in comprehensions`() = test("""
      async def asyncgen():
          yield 10
      async def run():
          expr = [await z for z in [asyncgen().__anext__()]]
      #       └ TYPE () -> CoroutineType[Unknown, Unknown, None] FIXME list[int]
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `await in async comprehensions`() = test("""
      async def asyncgen():
          yield 10
      async def asyncgen2():
          yield asyncgen().__anext__()
      async def run():
          expr = [await z async for z in asyncgen2()]
      #       └ TYPE () -> CoroutineType[Unknown, Unknown, None] FIXME list[int]
      """)

    @TestFor(issues = ["PY-22181"])
    @Test
    fun `async iteration over iterable with separate iterator`() = test("""
      class AIter(object):
          async def __anext__(self):
              return 5
      class A(object):
          def __aiter__(self):
              return AIter()
      async def run():
          a = A()
          async for expr in a:
      #             └ TYPE Literal[5]
              print(expr)
      """)

    @TestFor(issues = ["PY-60714"])
    @Test
    fun `async iterator unwraps coroutine from anext`() = test("""
      class AIterator:
          def __aiter__(self):
              return self

          async def __anext__(self) -> bytes:
              return b"a"

      async def run():
          async for expr in AIterator():
      #             └ TYPE bytes
              print(expr)
      """)

    @TestFor(issues = ["PY-41061"])
    @Test
    fun `for iteration over object with iter and aiter`() = test("""
      from collections.abc import Iterator, AsyncIterator

      class MyIterable:
          def __iter__(self) -> Iterator[int]: ...

          def __aiter__(self) -> AsyncIterator[str]: ...

      for expr in MyIterable(): ...
      #   └ TYPE int
      """)

    @TestFor(issues = ["PY-41061"])
    @Test
    fun `for iteration over object with iter and aiter in comprehension`() = test("""
      from collections.abc import Iterator, AsyncIterator

      class MyIterable:
          def __iter__(self) -> Iterator[int]: ...

          def __aiter__(self) -> AsyncIterator[str]: ...

      _ = [expr for expr in MyIterable()]
      #     └ TYPE int
      """)

    @TestFor(issues = ["PY-41061"])
    @Test
    fun `async for iteration over object with iter and aiter`() = test("""
      from collections.abc import Iterator, AsyncIterator

      class MyIterable:
          def __iter__(self) -> Iterator[int]: ...

          def __aiter__(self) -> AsyncIterator[str]: ...

      async def foo():
          async for expr in MyIterable(): ...
      #             └ TYPE str
      """)

    @TestFor(issues = ["PY-41061"])
    @Test
    fun `async for iteration over object with iter and aiter in comprehension`() = test("""
      from collections.abc import Iterator, AsyncIterator

      class MyIterable:
          def __iter__(self) -> Iterator[int]: ...

          def __aiter__(self) -> AsyncIterator[str]: ...

      async def foo():
          _ = [expr async for expr in MyIterable()]
      #         └ TYPE str
      """)

    @TestFor(issues = ["PY-24405"])
    @Test
    fun `async with type`() = test("""
      class AContext:
          async def __aenter__(self) -> str:
              pass
      async def foo():
          async with AContext() as c:
      #              ^^^^^^^^^^ WARNING Expected type 'contextlib.AbstractAsyncContextManager', got 'AContext' instead
              expr = c
      #       └ TYPE str
      """)

    @TestFor(issues = ["PY-29891"])
    @Test
    fun `async context manager`() = test("""
      from typing import AsyncContextManager
      async def example():
          manager: AsyncContextManager[str]
          async with manager as m:
              expr = m
      #       └ TYPE str
      """)

    @TestFor(issues = ["PY-24067"])
    @Test
    fun `async function return type in docstring`() = test("""
      async def f():
          '''
          :rtype: int
          '''
          pass
      expr = f()
      # └ TYPE CoroutineType[Unknown, Unknown, int]
      """)

    @TestFor(issues = ["PY-27518"])
    @Test
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
      """)

    @TestFor(issues = ["PY-26643"])
    @Test
    fun `replace self in coroutine`() = test("""
      class A:
          async def foo(self):
              return self
      class B(A):
          pass
      expr = B().foo()
      # └ TYPE CoroutineType[Unknown, Unknown, B]
      """)

    @Test
    fun `async generator annotation`() = test("""
      from typing import AsyncGenerator

      async def g() -> AsyncGenerator[int, str]:
          s = (yield 42)

      expr = g()
      # └ TYPE AsyncGenerator[int, str]
      """)

    @Test
    fun `coroutine returns generator`() = test("""
      from typing import Any, Generator

      async def coroutine() -> Generator[int, Any, Any]:
          def gen():
              yield 42

          return gen()

      expr = coroutine()
      #└ TYPE CoroutineType[Unknown, Unknown, Generator[int, Any, Any]]
      """)

    @TestFor(issues = ["PY-40458"])
    @Test
    fun `return type of non-annotated async override`() = test("""
      class Base:
          async def get(self) -> str:
              ...

      class Specific(Base):
          async def get(self):
              ...

      expr = Specific().get()
      # └ TYPE CoroutineType[Unknown, Unknown, str]
      """)

    @TestFor(issues = ["PY-40458"])
    @Test
    fun `return type of non-annotated async override of non-async method`() = test("""
      from typing import AsyncIterator, TypeGuard, Protocol

      class Base(Protocol):
          def get(self) -> AsyncIterator[int]:
              ...

      class Specific(Base):
          async def get(self):
              yield 42

      expr = Specific().get()
      # └ TYPE AsyncGenerator[Literal[42], Unknown]
      """)

    @TestFor(issues = ["PY-40458"])
    @Test
    fun `return type of non-annotated async override of async generator method`() = test("""
      from typing import AsyncIterator, TypeGuard, Protocol

      class Base(Protocol):
          async def get(self) -> AsyncIterator[int]:
              if False: yield

      class Specific(Base):
          async def get(self):
              yield 42

      expr = Specific().get()
      # └ TYPE AsyncIterator[int]
      """)

    @TestFor(issues = ["PY-21048"])
    @Test
    fun `async function return type is checked`() = test("""
      async def test(self, a) -> int:
          return 123
      """)

    @TestFor(issues = ["PY-20967"])
    @Test
    fun `async function annotated to return None`() = test("""
      async def f() -> None:
          print('foo')
      """)

    @TestFor(issues = ["PY-16898"])
    @Test
    fun `async for iterable assignability`() = test("""
      import asyncio
      from random import randint

      import collections


      class Cls(collections.AsyncIterable):
      #                     ^^^^^^^^^^^^^ WARNING Cannot find reference 'AsyncIterable' in 'collections'
          async def __aiter__(self):
              return self

          async def __anext__(self):
              data = await Cls.fetch_data()
              if data:
                  return data
              else:
                  print('iteration stopped')
                  raise StopAsyncIteration

          @staticmethod
          async def fetch_data():
              r = randint(1, 100)
              return r if r < 92 else False


      async def coro():
          a = Cls()
          async for i in a:  # OK
              await asyncio.sleep(0.2)
              print(i)
          else:
              print('end')

          async for i in []: # WARNING Expected type 'collections.AsyncIterable', got 'list[Unknown]' instead
              pass


      loop = asyncio.get_event_loop()
      loop.run_until_complete(coro())
      loop.close()
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `async for over async generator`() = test("""
      async def asyncgen():
          yield 10


      async def run():
          async for i in asyncgen():
              print(i)
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `for over async generator`() = test("""
      async def asyncgen():
          yield 10


      async def run():
          for i in asyncgen(): # WARNING Expected type 'collections.Iterable', got 'AsyncGenerator[Literal[10], Unknown]' instead
              print(i)
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `async comprehensions over async generator`() = test("""
      def check(x): ...
      async def asyncgen():
          yield 10
      async def run():
          {i async for i in asyncgen()}
          [i async for i in asyncgen()]
          {i: i ** 2 async for i in asyncgen()}
          (i ** 2 async for i in asyncgen())
          list(i async for i in asyncgen())

          dataset = {data async for line in asyncgen()
                          async for data in asyncgen()
                          if check(data)}
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `async comprehensions over plain generator`() = test("""
      def check(x): ...
      def gen():
          yield 10
      async def run():
          {i async for i in gen()} # WARNING Expected type 'collections.AsyncIterable', got 'Generator[Literal[10], Unknown, None]' instead
          [i async for i in gen()] # WARNING Expected type 'collections.AsyncIterable', got 'Generator[Literal[10], Unknown, None]' instead
          {i: i ** 2 async for i in gen()} # WARNING Expected type 'collections.AsyncIterable', got 'Generator[Literal[10], Unknown, None]' instead
          (i ** 2 async for i in gen()) # WARNING Expected type 'collections.AsyncIterable', got 'Generator[Literal[10], Unknown, None]' instead
          list(i async for i in gen()) # WARNING Expected type 'collections.AsyncIterable', got 'Generator[Literal[10], Unknown, None]' instead

          dataset = {data async for line in gen()
      #                                     ^^^^^ WARNING Expected type 'collections.AsyncIterable', got 'Generator[Literal[10], Unknown, None]' instead
                          async for data in gen()
      #                                     ^^^^^ WARNING Expected type 'collections.AsyncIterable', got 'Generator[Literal[10], Unknown, None]' instead
                          if check(data)}
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `comprehensions over async generator`() = test("""
      def check(x): ...
      async def asyncgen():
          yield 10
      async def run():
          {i for i in asyncgen()} # WARNING Expected type 'collections.Iterable', got 'AsyncGenerator[Literal[10], Unknown]' instead
          [i for i in asyncgen()] # WARNING Expected type 'collections.Iterable', got 'AsyncGenerator[Literal[10], Unknown]' instead
          {i: i ** 2 for i in asyncgen()} # WARNING Expected type 'collections.Iterable', got 'AsyncGenerator[Literal[10], Unknown]' instead
          (i ** 2 for i in asyncgen()) # WARNING Expected type 'collections.Iterable', got 'AsyncGenerator[Literal[10], Unknown]' instead
          list(i for i in asyncgen()) # WARNING Expected type 'collections.Iterable', got 'AsyncGenerator[Literal[10], Unknown]' instead

          dataset = {data for line in asyncgen()
      #                               ^^^^^^^^^^ WARNING Expected type 'collections.Iterable', got 'AsyncGenerator[Literal[10], Unknown]' instead
                          for data in asyncgen()
      #                               ^^^^^^^^^^ WARNING Expected type 'collections.Iterable', got 'AsyncGenerator[Literal[10], Unknown]' instead
                          if check(data)}
      """)

    @TestFor(issues = ["PY-20657", "PY-21916"])
    @Test
    fun `async generator annotated to return async iterable`() = test("""
      from typing import AsyncIterable


      async def g1() -> AsyncIterable[int]:
          yield 42

      async def g2() -> AsyncIterable[int]:
          yield 42
          return None # ERROR non-empty 'return' inside asynchronous generator

      async def g3() -> AsyncIterable:
          yield 42

      async def g4() -> AsyncIterable:
          yield 42
          return None # ERROR non-empty 'return' inside asynchronous generator
      """)

    @TestFor(issues = ["PY-20770"])
    @Test
    fun `structural type async for requires aiter`() = test("""
      async def async_for(p):
          async for i in p:
              pass


      async def async_iter():
          yield 42


      async_for(async_iter())
      async_for([1, 2, 3]) # WARNING Type 'list[Literal[1, 2, 3]]' doesn't have expected attribute '__aiter__'
      """)
  }

  @Test
  @TestFor(issues = ["PY-22391"])
  fun `iterating over list after if not`() = test("""
    value = []

    if not value:
        print([a for a in value])
    """)

  @Test
  @TestFor(issues = ["PY-30629"])
  fun `iterating over abstract method result is not reported`() = test("""
    from abc import ABCMeta, abstractmethod

    class A(metaclass=ABCMeta):

        @abstractmethod
        def foo(self):
            pass

    def something(derived: A):
        for _, _ in derived.foo():
            pass
    """)

  @Test
  @TestFor(issues = ["PY-25120"])
  fun `iterate over dict value when its type is union`() = test("""
    KWARGS = {
        "do_stuff": True,
        "little_list": ['a', 'b'],
    }

    for element in KWARGS["little_list"]: # WARNING Expected type 'collections.Iterable', got 'bool | list[str]' instead
        print(element)
    """)

  @Test
  fun `class is iterable only when its metaclass defines dunder iter`() = test("""
    from collections.abc import Iterator

    class M1(type):
        def __iter__(self) -> Iterator[int]: ...

    class M2(type):
        pass

    class C1(metaclass=M1):
        pass

    class C2(metaclass=M2):
        pass

    class B1(C1):
        pass

    for x in C1:
        pass

    for y in C2: # WARNING Expected type 'collections.Iterable', got 'type[C2]' instead
        pass

    for z in B1:
        pass
    """)

  @Nested
  inner class PyAnyMigrationMirrors {

    val oldAny = defaultTestOptions.copy(enablePyAnyType = false)

    @Test
    fun `generator function type (old py-any)`() = test(
      oldAny,
      """
      def f():
          yield 'foo'
          return 0

      expr = f()
      # └ TYPE Generator[Literal["foo"], Any, Literal[0]]
      """)

    @Test
    fun `async generator (old py-any)`() = test(
      oldAny,
      """
      async def asyncgen():
          yield 42
      expr = asyncgen()
      #└ TYPE AsyncGenerator[Literal[42], Any]
      """)
  }
}

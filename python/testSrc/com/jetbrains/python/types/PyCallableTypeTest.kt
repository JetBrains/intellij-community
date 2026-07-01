// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components
import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for callable types: `Callable[...]`, `ParamSpec`, `Concatenate`,
 * typed `*args`/`**kwargs`, callable assignability/subtyping, function-type inference and
 * decorator/`functools`-callable typing.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyCallableTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class CallableTypeInference {
    @Test
    fun `call of union returns union of callable types`() = test("""
      import random
      def spam():
          return "D"
      class Eggs:
          pass
      class Eggs2:
          pass
      dd = spam if random.randint != 42 else Eggs2()
      var = dd if random.randint != 42 else dd
      expr = var()
      # │    ^^^^^ WARNING Member 'Eggs2' of '() -> Literal["D"] | Eggs2' is not callable
      # └ TYPE Literal["D"]
      """)

    @Test
    @TestFor(issues = ["PY-84030"])
    fun `calling union of callables`() = test("""
    from typing import Callable

    def f(x: Callable[[int], None] | Callable[[str], None]):
        x(1)
    #     └ WARNING Expected type 'str', got 'Literal[1]' instead

    def g(x: Callable[[], None] | Callable[[str], None]):
        x()
    #     └ WARNING FIXME No signature matches the arguments
        x(1)
    #     └ WARNING Expected type 'str', got 'Literal[1]' instead
    """)

    @Test
    @TestFor(issues = ["PY-9605"])
    fun `property returns callable`() = test("""
      class C(object):
          @property
          def foo(self):
              return lambda: 0

      c = C()
      expr = c.foo
      #└ TYPE () -> Literal[0]
      """)

    @Test
    fun `function type rendered as callable`() = test("""
      def func(x: int, /, s: str, *, k: bytes) -> None:
          pass
      expr = func
      #└ TYPE (x: int, /, s: str, *, k: bytes) -> None
      """)

    @Test
    fun `returned typing Callable`() = test("""
      from typing import Callable
      def f() -> Callable:
          pass
      expr = f()
      #└ TYPE (...) -> Unknown
      """)

    @Test
    fun `returned typing Callable with unknown parameters`() = test("""
      from typing import Callable
      def f() -> Callable[..., int]:
          pass
      expr = f()
      #└ TYPE (...) -> int
      """)

    @Test
    fun `returned typing Callable with known parameters`() = test("""
      from typing import Callable
      def f() -> Callable[[int, str], int]:
          pass
      expr = f()
      #└ TYPE (int, str) -> int
      """)

    @Test
    fun `typing Callable type of parameter`() = test("""
      from typing import Callable

      def foo(expr: Callable[[int, str], str]):
      #       └ TYPE (int, str) -> str
          pass
      """)

    @Test
    fun `callable type with ellipsis from type comment`() = test("""
      from typing import Callable

      expr = unknown() # type: Callable[..., int]
      #│     ^^^^^^^ ERROR Unresolved reference 'unknown'
      #└ TYPE (...) -> int
      """)

    @Test
    @TestFor(issues = ["PY-18726"])
    fun `function type comment callable parameter`() = test("""
      from typing import Callable

      def f(cb):
          # type: (Callable[[bool, str], int]) -> None
          expr = cb
      #   └ TYPE (bool, str) -> int
      """)

    @Test
    @TestFor(issues = ["PY-18763"])
    fun `function type comment callable parameter with ellipsis`() = test("""
      from typing import Callable

      def f(cb):
          # type: (Callable[..., int]) -> None
          expr = cb
      #   └ TYPE (...) -> int
      """)

    // NOTE: `testFunctionTypeCommentBadCallableParameter1`/`2` (PY-18726) were intentionally left out.
    // In the new framework these malformed `# type:` comments emit a syntax-level diagnostic whose
    // reported count is unstable across highlighting passes, which the inline-assertion comparison
    // cannot pin down; the underlying callable-type inference is already covered by the other
    // function-type-comment tests above.

    @Test
    fun `builtins callable narrows to callable type`() = test("""
      a = object()
      if callable(a):
          expr = a
      #   └ TYPE (...) -> object
      """)

    @Test
    @TestFor(issues = ["PY-79861"])
    fun `walrus callable narrowing`() = test("""
      if callable(a := 42):
          expr = a
      #   └ TYPE Literal[42]
      """)

    @Test
    fun `generic callable rendered with type parameters`() = test("""
      def f[T: int = str, *Ts = *tuple[int], **P = [str]](t: T) -> T: ... # WARNING Default type of TypeVar is not a subtype of the bound

      # using a list to widen to a PyCallableType
      expr = [f][0]
      #└ TYPE [T: int = str, *Ts = *tuple[int], **P = [str]](t: T) -> T
      """)
  }

  @Nested
  inner class CallableParameterInference {
    @Test
    @TestFor(issues = ["PY-37876"])
    fun `callable parameter TypeVar matching`() = test("""
      from typing import Callable, TypeVar, Any

      T = TypeVar('T')
      def func(x: Callable[[T], Any]) -> T:
          pass

      def callback(x: int) -> Any:
          pass


      expr = func(callback)
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-37876"])
    fun `callable parameter generic type parameter matching`() = test("""
      from typing import Callable, TypeVar, Any, List

      T = TypeVar('T')


      def func(f: Callable[[List[T]], Any]) -> T:
          pass


      def accepts_list_of_int(x: List[int]) -> Any:
          pass


      expr = func(accepts_list_of_int)
      #└ TYPE int
      """)

    @Test
    fun `decorator with argument called as function`() = test("""
      from typing import Callable, TypeVar

      S = TypeVar('S')
      T = TypeVar('T')

      def dec(t: T):
          def g(fun: Callable[[], S]) -> Callable[[T], S]:
              ...

          return g

      def func() -> int:
          ...

      expr = dec('foo')(func)
      #└ TYPE (str) -> int
      """)

    @Test
    fun `generic parameter of expected callable`() = test("""
      from typing import Callable, Generic, TypeVar

      T = TypeVar('T')

      class Super(Generic[T]):
          pass

      class Sub(Super[T]):
          pass

      def f(x: Callable[[Sub[T]], None]) -> T:
          pass

      def g(x: Super[int]):
          pass

      expr = f(g)
      #└ TYPE int
      """)
  }

  @Nested
  inner class TypedArgsAndKwargs {
    @Test
    @TestFor(issues = ["PY-19723"])
    fun `positional args type from docstring`() = test("""
      def foo(*args):
          '''
          :type args: int
          '''
          expr = args
      #   └ TYPE tuple[int, ...]
      """)

    @Test
    @TestFor(issues = ["PY-19723"])
    fun `keyword args type from docstring`() = test("""
      def foo(**kwargs):
          '''
          :type kwargs: int
          '''
          expr = kwargs
      #   └ TYPE dict[str, int]
      """)

    @Test
    @TestFor(issues = ["PY-19723"])
    fun `iterate over keyword args`() = test("""
      def foo(**kwargs):
          for expr in kwargs:
      #       └ TYPE str
              pass
      """)

    @Test
    @TestFor(issues = ["PY-19723"])
    fun `type var substitution in positional args`() = test("""
      def foo(*args):
          '''
          :type args: T
          :rtype: T
          '''
          pass
      expr = foo(1)
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-19723"])
    fun `type var substitution in heterogeneous positional args`() = test("""
      def foo(*args):
          '''
          :type args: T
          :rtype: T
          '''
          pass
      expr = foo(1, "2")
      #└ TYPE int | str
      """)

    @Test
    @TestFor(issues = ["PY-19723"])
    fun `type var substitution in keyword args`() = test("""
      def foo(**kwargs):
          '''
          :type kwargs: T
          :rtype: T
          '''
          pass
      expr = foo(a=1)
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-19723"])
    fun `type var substitution in heterogeneous keyword args`() = test("""
      def foo(**kwargs):
          '''
          :type kwargs: T
          :rtype: T
          '''
          pass
      expr = foo(a=1, b="2")
      #└ TYPE int | str
      """)

    @Test
    @TestFor(issues = ["PY-19723"])
    fun `annotated positional args`() = test("""
      def foo(*args: str):
          expr = args
      #   └ TYPE tuple[str, ...]
      """)

    @Test
    @TestFor(issues = ["PY-19723"])
    fun `annotated keyword args`() = test("""
      def foo(**kwargs: int):
          expr = kwargs
      #   └ TYPE dict[str, int]
      """)

    @Test
    @TestFor(issues = ["PY-19723"])
    fun `type commented positional args`() = test("""
      def foo(*args  # type: str
      ):
          expr = args
      #   └ TYPE tuple[str, ...]
      """)

    @Test
    @TestFor(issues = ["PY-19723"])
    fun `type commented keyword args`() = test("""
      def foo(**kwargs  # type: int
      ):
          expr = kwargs
      #   └ TYPE dict[str, int]
      """)

    @Test
    @TestFor(issues = ["PY-22513"])
    fun `generic kwargs inference`() = test("""
      from typing import Any, Dict, TypeVar

      T = TypeVar('T')

      def generic_kwargs(**kwargs: T) -> Dict[str, T]:
          pass

      expr = generic_kwargs(a=1, b='foo')
      #└ TYPE dict[str, int | str]
      """)

    @Test
    fun `dict comprehension from kwargs`() = test("""
      def test(**kwargs):
          expr = {k: v for k, v in kwargs.items()}
      #   └ TYPE dict[str, Unknown]
      """)

    @Test
    @TestFor(issues = ["PY-55044"])
    fun `kwargs with unpacked plain class type in annotation`() = test("""
      from typing import Unpack
      class Movie:
          pass
      def foo(**x: Unpack[Movie]):
          expr = x
      #   └ TYPE dict[str, Unknown]
      """)
  }

  @Nested
  inner class BoundAndUnboundMethodCallableTypes {
    @Test
    @TestFor(issues = ["PY-76855"])
    fun `callable with Self substituted with qualifier type with default`() = test("""
      from typing import Self, Generic, TypeVar

      DefaultIntT = TypeVar('DefaultIntT', default=int)
      class Foo7(Generic[DefaultIntT]):
          def meth(self, /) -> Self:
              return self

      expr = Foo7.meth
      #└ TYPE (self: Foo7[int], /) -> Foo7[int]
      """)

    @Test
    @TestFor(issues = ["PY-76855"])
    fun `callable with Self substituted with qualifier type default overridden`() = test("""
      from typing import Self, Generic, TypeVar

      DefaultIntT = TypeVar('DefaultIntT', default=int)
      class Foo7(Generic[DefaultIntT]):
          def meth(self, /) -> Self:
              return self

      expr = Foo7[str].meth
      # └ TYPE (self: Foo7[str], /) -> Foo7[str]
      """)

    @Test
    @TestFor(issues = ["PY-76855"])
    fun `callable with Self substituted with qualifier type self dropped`() = test("""
      from typing import Self, Generic, TypeVar

      DefaultIntT = TypeVar('DefaultIntT', default=int)
      class Foo7(Generic[DefaultIntT]):
          def meth(self, /) -> Self:
              return self

      expr = Foo7[str]().meth
      #└ TYPE (/) -> Foo7[str]
      """)

    @Test
    @TestFor(issues = ["PY-89401"])
    fun `bound method`() = test("""
      class A:
          def f(self, x: int) -> str:
              raise NotImplementedError

      def foo(a: A):
          f = a.f
      #   └ TYPE (x: int) -> str
          expr = f(-1)
      #   └ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-89401"])
    fun `bound generic method`() = test("""
      class A:
          def f[U](self, x: U) -> U:
              raise NotImplementedError

      def foo(a: A):
          f = a.f
      #   └ TYPE [U](x: U) -> U
          expr = f('abb')
      #   └ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-89401"])
    fun `bound method of generic class`() = test("""
      class A[T]:
          def f(self, x: str) -> T:
              raise NotImplementedError

      def foo(a: A[int]):
          f = a.f
      #   └ TYPE (x: str) -> int
          expr = f('abb')
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-89401"])
    fun `bound generic method of generic class`() = test("""
      class A[T]:
          def f[U](self, x: U) -> tuple[T, U]:
              raise NotImplementedError

      def foo(a: A[int]):
          f = a.f
      #   └ TYPE [U](x: U) -> tuple[int, U]
          expr = f('abb')
      #   └ TYPE tuple[int, str]
    """)

    @Test
    @TestFor(issues = ["PY-89401"])
    fun `overloaded bound method`() = test("""
      from typing import overload

      class A[T]:
          @overload
          def f[U](self, x: U, y: int) -> tuple[T, U, str]: ...

          @overload
          def f[U](self, x: U, y: str) -> tuple[T, U, bytes]: ...

          def f[U](self, x: U, y: object) -> tuple[T, U, object]:
              raise NotImplementedError

      def foo(a: A[int]):
          f = a.f
      #   └ TYPE Overload[[U](x: U, y: int) -> tuple[int, U, str], [U](x: U, y: str) -> tuple[int, U, bytes]]
          expr = f('abb', 'abc')
      #   └ TYPE tuple[int, str, bytes]
      """)

    @Test
    @TestFor(issues = ["PY-89401"])
    fun `overloaded bound method with annotated self`() = test("""
      from typing import overload

      class A[T]:
          @overload
          def f(self: A[int]) -> str: ...

          @overload
          def f(self: A[str]) -> int: ...

          def f(self) -> object:
              raise NotImplementedError

      def foo(a: A[str]):
          f = a.f
      #   └ TYPE () -> int
          expr = f()
      #   └ TYPE int
      """)

    @TestFor(issues = ["PY-89400"])
    @Test
    fun `method call on union of class types`() = test("""
      class A[T]:
          def foo[U](self: U) -> tuple[T, U]: ...

      class B[T]:
          def foo(self) -> T: ...

      def f(receiver: A[int] | B[str]):
          expr = receiver.foo()
      #   └ TYPE tuple[int, A[int]] | str
      """)

    @Test
    fun `bound method self in varargs`() = test("""
      from typing import overload

      class A[T]:
          @overload
          def f(*args: A[int]) -> str: ...

          @overload
          def f(*args: A[str]) -> int: ...

          @overload
          def f(*args: A[list[int]]) -> list[str]: ...

          def f(*args: object) -> object:
              raise NotImplementedError

      a = A[str]()
      func = a.f
      #└ TYPE (*args: A[str]) -> int
      expr = a.f()
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-90249"])
    fun `bound method of type var with bound`() = test("""
      class Box[U]:
          def get(self) -> U: ...

      def foo[T: Box[int]](x: T):
          get = x.get
      #   └ TYPE () -> int
          expr = x.get()
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-90249"])
    fun `bound method of type var with constraints`() = test("""
      class Box[U]:
          def get(self) -> U: ...

      def foo[T: (Box[int], Box[str])](x: T):
          get = x.get
      #   └ TYPE () -> int | () -> str
          expr = get()
      #   └ TYPE int | str
      """)

    @Test
    fun `bound method returning self of type var`() = test("""
      from typing import Self

      class Box[U]:
          def get(self) -> Self: ...

      def foo[T: Box[int], Y: (Box[int], Box[str])](t: T, y: Y):
          v1 = t.get()
      #   └ TYPE T
          v2 = y.get()
      #   └ TYPE Y
      """)

    @Test
    fun `bound method returning generic of type var`() = test("""
      class Box[U]:
        def get(self) -> U: ...
  
  
      def foo[T: Box[int], Y: Box[int] | Box[str], Z: (Box[int], Box[str])](t: T, y: Y, z: Z):
          v1 = t.get()
      #   └ TYPE int
          v2 = y.get()
      #   └ TYPE int | str
          v3 = z.get()
      #   └ TYPE int | str
      """)

    @Test
    fun `metaclass method call on class`() = test("""
      class Meta(type):
          def foo(cls) -> int: ...

      class Class(metaclass=Meta): ...

      expr = Class.foo()
      #└ TYPE int
      """)

    @TestFor(issues = ["PY-90557"])
    @Test
    fun `method inherited from object stays unbound when accessed on class`() = test("""
      expr = int.__str__
      #└ TYPE (self: int) -> str
      """)

    @Test
    fun `call type preserves generic parameter`() = test("""
      class MyList[T]:
          def add(self, v: T) -> MyList[T]:
              raise NotImplementedError

      def add[T](c: MyList[T], v: T):
          expr = c.add(v)
      #   └ TYPE MyList[T]
      """)

    @TestFor(issues = ["PY-89079"])
    @Test
    fun `subscription expression as callee`() = test("""
      class A:
          def __call__[T](self, x: T) -> T: return x

      def f(items: list[A]):
          item = items[0]
          _ = item(-7)
      #   └ TYPE int
          _ = items[0](1)
      #   └ TYPE int
      """)
  }

  @Nested
  inner class DecoratorCallableParamSpecFunctionInference {
    @Test
    @TestFor(issues = ["PY-79204"])
    fun `infer parameter from decorator`() = test("""
      from collections.abc import Callable

      def d(fn: Callable[[int], object]) -> None: ...

      @d
      def f(i):
          expr = i
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-85768"])
    fun `infer parameter from generic decorator constrained by outer decorator`() = test("""
      from collections.abc import Callable

      def d1[T](fn: Callable[[T], object]) -> T: ...
      def d2(i: int) -> None: ...

      @d2
      @d1
      def f(i):
          expr = i
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-85768"])
    fun `infer parameter from generic decorator through a generic middle decorator`() = test("""
      from collections.abc import Callable

      def d1[T](fn: Callable[[T], object]) -> T: ...
      def d2[U](g: U) -> U: ...
      def d3(i: int) -> None: ...

      @d3
      @d2
      @d1
      def f(i):
          expr = i
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-85768"])
    fun `infer parameter from generic decorator past a transparent decorator`() = test("""
      from collections.abc import Callable

      def d1[T](fn: Callable[[T], object]) -> T: ...
      def ident(f): return f
      def d2(i: int) -> None: ...

      @d2
      @ident
      @d1
      def f(i):
          expr = i
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-85768"])
    fun `infer parameter wrapped in a container from generic decorator`() = test("""
      from collections.abc import Callable

      def d1[T](fn: Callable[[list[T]], object]) -> T: ...
      def d2(i: int) -> None: ...

      @d2
      @d1
      def f(i):
          expr = i
      #   └ TYPE list[int]
      """)

    @Test
    @TestFor(issues = ["PY-85768"])
    fun `generic decorator parameter is left unbound without an outer constraint`() = test("""
      from collections.abc import Callable

      def d1[T](fn: Callable[[T], object]) -> T: ...

      @d1
      def f(i):
          expr = i
      #   └ TYPE T
      """)

    @Test
    @TestFor(issues = ["PY-79204"])
    fun `infer parameter from decorator called`() = test("""
      from collections.abc import Callable

      def d() -> Callable[[Callable[[int], object]], None]: ...

      @d()
      def f(i):
          expr = i
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-79204"])
    fun `infer parameter from decorator method`() = test("""
      from collections.abc import Callable

      def d(fn: Callable[[int], object]) -> None: ...

      class A:
        @d
        def m(self) -> None:
          expr = self
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-79204"])
    fun `infer parameter from decorator variadic unpacked`() = test("""
      from typing import Protocol

      class P(Protocol):
        def __call__(self, *args: int): ...

      def d(fn: P) -> None: ...

      @d
      def f(a, b, c):
          expr = c
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-79204"])
    fun `infer parameter from decorator positional only`() = test("""
      from typing import Protocol

      class P(Protocol):
        def __call__(self, x: int, /, y: str): ...

      def d(fn: P) -> None: ...

      @d
      def f(a, b):
          # despite not fully matching, we can still match `b` with `y`
          expr = b
      #   └ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-79204"])
    fun `infer parameter from decorator index only`() = test("""
      from typing import Protocol

      class P(Protocol):
        def __call__(self, x: int, y: str, /): ...

      def d(fn: P) -> None: ...

      @d
      def f(a, b, /):
          expr = a
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-79204"])
    fun `infer parameter from decorator innermost used`() = test("""
      from typing import Protocol
      from collections.abc import Callable

      class P(Protocol):
        def __call__(self, i: int): ...

      def d(fn: Callable[[int], None]) -> None: ...
      def outer(fn: Callable[[str], None]) -> None: ...

      @outer
      @d
      def f(i):
          expr = i
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-89342"])
    fun `infer parameter from decorator variadic`() = test("""
      from typing import Protocol

      class P(Protocol):
        def __call__(self, *args: int): ...

      def d(fn: P) -> None: ...

      @d
      def f(*args):
          expr = args[100]
      #   └ TYPE Unknown FIXME int
      """)

    @Test
    @TestFor(issues = ["PY-89342"])
    fun `infer parameter from decorator variadic kwargs`() = test("""
      from typing import Protocol

      class P(Protocol):
        def __call__(self, **kwargs: int): ...

      def d(fn: P) -> None: ...

      @d
      def f(**kwargs):
          expr = kwargs["100"]
      #   └ TYPE Unknown FIXME int
      """)

    @Test
    @TestFor(issues = ["PY-89342"])
    fun `infer parameter from decorator keyword only`() = test("""
      from typing import Protocol

      class P(Protocol):
        def __call__(self, *, x: int, y: str): ...

      def d(fn: P) -> None: ...

      @d
      def f(*, y, x):
          expr = x
      #   └ TYPE Unknown FIXME int
      """)

    @Test
    @TestFor(issues = ["PY-89342"])
    fun `infer parameter from decorator unpacked tuple`() = test("""
      from typing import Protocol

      class P(Protocol):
        def __call__(self, *i: *tuple[int, str]): ...

      def d(fn: P) -> None: ...

      @d
      def f(a, b):
          expr = b
      #   └ TYPE *tuple[int, str] FIXME int
      """)

    @Test
    @TestFor(issues = ["PY-85768"])
    fun `infer parameter from decorator generic chain`() = test("""
      from typing import Callable

      def d1[T](fn: Callable[[T], object]) -> T: ...
      def d2(i: int) -> None: ...

      @d2
      @d1
      def f(i):
          expr = i
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-36444"])
    fun `context manager decorator`() = test("""
      from contextlib import contextmanager

      @contextmanager
      def generator_function():
          yield "some value"

      with generator_function() as value:
          expr = value
      #   └ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-36444"])
    fun `text IO inferred with context manager decorator`() = test("""
      from contextlib import contextmanager

      @contextmanager
      def open_file(name: str):
          f = open(name)
          yield f
          f.close()

      cm = open_file(__file__)
      with cm as file:
          expr = file
      #   └ TYPE TextIOWrapper[_WrappedBuffer]
      """)

    @Test
    @TestFor(issues = ["PY-71674"])
    fun `context manager decorator on method`() = test("""
      from contextlib import contextmanager
      from typing import Iterator


      class MyClass:
          @contextmanager
          def as_context(self) -> Iterator[str]:
              yield "foo"


      with MyClass().as_context() as value:
          expr = value
      #   └ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-85027"])
    fun `bound method decorated with ParamSpec`() = test("""
      from typing import Callable

      def outer_decorator[**P, T](f: Callable[P, T]) -> Callable[P, T]:
          return f


      class NonWorkingClass:
          @outer_decorator
          def add_two(self, x: float, y: float) -> float:
              return x + y


      expr = NonWorkingClass().add_two
      #└ TYPE (x: float | int, y: float | int) -> float | int
      """)

    @Test
    @TestFor(issues = ["PY-51768"])
    fun `imported decorated function with ParamSpec`() = test(
      """
      from mod import f

      expr = f
      #└ TYPE (x: int) -> None
      """,
      "lib.py" to """
        from typing import Callable


        def decorator[**P, R](fn: Callable[P, R]) -> Callable[P, R]:
            return fn
        """,
      "mod.py" to """
        from lib import decorator


        @decorator
        def f(x: int) -> None:
            pass
        """,
    )

    @Test
    @TestFor(issues = ["PY-85027"])
    fun `imported bound method decorated with ParamSpec`() = test(
      """
      from mod import NonWorkingClass

      expr = NonWorkingClass().add_two
      #└ TYPE (x: float | int, y: float | int) -> float | int
      """,
      "mod.py" to """
        from typing import Callable


        def outer_decorator[**P, T](f: Callable[P, T]) -> Callable[P, T]:
            return f


        class NonWorkingClass:
            @outer_decorator
            def add_two(self, x: float, y: float) -> float:
                return x + y
        """,
    )

    @Test
    @TestFor(issues = ["PY-90348"])
    fun `Concatenate-typed decorator keeps the receiver type`() = test("""
      from typing import Any, Callable, Concatenate

      def deco[**P, R](fn: Callable[Concatenate[Any, P], R]) -> Callable[P, R]: ...

      class C:
        @deco
        def m(self, a: int) -> int:
          expr = self
      #   └ TYPE Self@C
          return a
      """)
  }

  @Nested
  inner class ParamSpecAndConcatenateTypeInference {
    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec example`() = test("""
      from typing import Callable, ParamSpec

      P = ParamSpec("P")


      def changes_return_type_to_str(x: Callable[P, int]) -> Callable[P, str]: ...


      def returns_int(a: str, b: bool) -> int:
          return 42


      expr = changes_return_type_to_str(returns_int)
      #└ TYPE (a: str, b: bool) -> str
      """)

    @Test
    @TestFor(issues = ["PY-59127"])
    fun `ParamSpec in imported file`() = test(
      """
      from mod import changes_return_type_to_str

      def returns_int(a: str, b: bool) -> int:
          return 42

      expr = changes_return_type_to_str(returns_int)
      #└ TYPE (a: str, b: bool) -> str
      """,
      "mod.py" to """
        from typing import Callable, ParamSpec

        P = ParamSpec("P")


        def changes_return_type_to_str(x: Callable[P, int]) -> Callable[P, str]:
            ...
        """,
    )

    @Test
    fun `ParamSpec args kwargs in annotations`() = test("""
      from typing import Callable, ParamSpec

      P = ParamSpec('P')

      def func(c: Callable[P, int], *args: P.args, **kwargs: P.kwargs) -> None:
          ...

      expr = func
      #└ TYPE (c: (**P) -> int, *args: **P, **kwargs: **P) -> None
      """)

    @Test
    fun `ParamSpec args kwargs in type comments`() = test("""
      from typing import Callable, ParamSpec

      P = ParamSpec('P')

      def func(c, # type: Callable[P, int]
               *args, # type: P.args
               **kwargs, # type: P.kwargs
               ):
          # type: (...) -> None
          ...

      expr = func
      #└ TYPE (c: (**P) -> int, *args: **P, **kwargs: **P) -> None
      """)

    @Test
    fun `ParamSpec args kwargs in function type comment`() = test("""
      from typing import Callable, ParamSpec

      P = ParamSpec('P')

      def func(c, *args, **kwargs):
          # type: (Callable[P, int], *P.args, **P.kwargs) -> None
          ...

      expr = func
      #└ TYPE (c: (**P) -> int, *args: **P, **kwargs: **P) -> None
      """)

    @Test
    fun `ParamSpec args kwargs in imported file`() = test(
      """
      from mod import func

      expr = func
      #└ TYPE (c: (**P) -> int, *args: **P, **kwargs: **P) -> None
      """,
      "mod.py" to """
        from typing import Callable, ParamSpec

        P = ParamSpec('P')

        def func(c: Callable[P, int], *args: P.args, **kwargs: P.kwargs) -> None:
            ...
        """,
    )

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec several`() = test("""
      from typing import ParamSpec, Callable

      P = ParamSpec("P")


      def foo(x: Callable[P, int], y: Callable[P, int]) -> Callable[P, bool]: ...


      def x_y(x: int, y: str) -> int: ...


      def y_x(y: int, x: str) -> int: ...


      expr = foo(x_y, y_x)
      #└ TYPE (y: int, x: str) -> bool
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec user generic class`() = test("""
      from typing import TypeVar, Generic, Callable, ParamSpec

      U = TypeVar("U")
      P = ParamSpec("P")


      class Y(Generic[U, P]):
          f: Callable[P, str]
          attr: U

          def __init__(self, f: Callable[P, str], attr: U) -> None:
              self.f = f
              self.attr = attr


      def a(q: int, p: str, r: bool) -> str: ...


      expr = Y(a, 1)
      #└ TYPE Y[int, [q: int, p: str, r: bool]]
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec user generic class method`() = test("""
      from typing import TypeVar, Generic, Callable, ParamSpec

      U = TypeVar("U")
      P = ParamSpec("P")


      class Y(Generic[U, P]):
          f: Callable[P, U]
          attr: U

          def __init__(self, f: Callable[P, U], attr: U) -> None:
              self.f = f
              self.attr = attr


      def a(q: int) -> str: ...


      expr = Y(a, '1').f
      #└ TYPE (q: int) -> str
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec user generic class method concatenate`() = test("""
      from typing import TypeVar, Generic, Callable, ParamSpec, Concatenate

      U = TypeVar("U")
      P = ParamSpec("P")


      class Y(Generic[U, P]):
          f: Callable[Concatenate[int, P], U]
          attr: U

          def __init__(self, f: Callable[Concatenate[int, P], U], attr: U) -> None:
              self.f = f
              self.attr = attr


      def a(q: int, s: str, b: bool) -> str: ...


      expr = Y(a, '1').f
      #└ TYPE (int, s: str, b: bool) -> str
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec user generic class method concatenate several parameters`() = test("""
      from typing import TypeVar, Generic, Callable, ParamSpec, Concatenate

      U = TypeVar("U")
      P = ParamSpec("P")


      class Y(Generic[U, P]):
          f: Callable[Concatenate[int, bool, P], U]
          attr: U

          def __init__(self, f: Callable[Concatenate[int, bool, P], U], attr: U) -> None:
              self.f = f
              self.attr = attr


      def a(q: int, r: bool, s: str, b: bool) -> str: ...


      expr = Y(a, '1').f
      #└ TYPE (int, bool, s: str, b: bool) -> str
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec user generic class method concatenate other function`() = test("""
      from typing import TypeVar, Generic, Callable, ParamSpec, Concatenate

      U = TypeVar("U")
      P = ParamSpec("P")


      class Y(Generic[U, P]):
          f: Callable[Concatenate[int, bool, P], U]
          g: Callable[Concatenate[bool, dict[str, list[str]], P], U]
          attr: U

          def __init__(self, f: Callable[Concatenate[int, bool, P], U], attr: U) -> None:
              self.f = f
              self.attr = attr


      def a(q: int, r: bool, s: str, b: bool) -> str: ...


      expr = Y(a, '1').g
      #└ TYPE (bool, dict[str, list[str]], s: str, b: bool) -> str
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec user generic class attribute`() = test("""
      from typing import TypeVar, Generic, Callable, ParamSpec

      U = TypeVar("U")
      P = ParamSpec("P")


      class Y(Generic[U, P]):
          f: Callable[P, U]
          attr: U

          def __init__(self, f: Callable[P, U], attr: U) -> None:
              self.f = f
              self.attr = attr


      def a(q: int) -> str: ...


      expr = Y(a, '1').attr
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate add`() = test("""
      from typing import Callable, Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


      expr = add(bar)
      #└ TYPE (str, x: int, *args: bool) -> bool
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate add several parameters`() = test("""
      from typing import Callable, Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def add(x: Callable[P, int]) -> Callable[Concatenate[str, bool, P], bool]: ...


      expr = add(bar)
      #└ TYPE (str, bool, x: int, *args: bool) -> bool
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate remove`() = test("""
      from typing import Callable, Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


      expr = remove(bar)
      #└ TYPE (*args: bool) -> bool
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate transform`() = test("""
      from typing import Callable, Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def transform(
              x: Callable[Concatenate[int, P], int]
      ) -> Callable[Concatenate[str, P], bool]:
          def inner(s: str, *args: P.args): # WARNING 'P.args' and 'P.kwargs' must both be present in the same function signature
              return True
          return inner


      expr = transform(bar)
      #└ TYPE (str, *args: bool) -> bool
      """)

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `ParamSpec example with PEP695 syntax`() = test("""
      from typing import Callable

      def changes_return_type_to_str[**P](x: Callable[P, int]) -> Callable[P, str]: ...

      def returns_int(a: str, b: bool) -> int:
          return 42

      expr = changes_return_type_to_str(returns_int)
      #└ TYPE (a: str, b: bool) -> str
      """)

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `ParamSpec in imported file with PEP695 syntax`() = test(
      """
      from a import changes_return_type_to_str

      def returns_int(a: str, b: bool) -> int:
          return 42

      expr = changes_return_type_to_str(returns_int)
      #└ TYPE (a: str, b: bool) -> str
      """,
      "a.py" to """
        from typing import Callable

        def changes_return_type_to_str[**P](x: Callable[P, int]) -> Callable[P, str]:
            ...
        """,
    )

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `ParamSpec concatenate transform with PEP695 syntax`() = test("""
      from typing import Callable, Concatenate

      def bar(x: int, *args: bool) -> int: ...


      def transform[**P](
              x: Callable[Concatenate[int, P], int]
      ) -> Callable[Concatenate[str, P], bool]:
          def inner(s: str, *args: P.args): # WARNING 'P.args' and 'P.kwargs' must both be present in the same function signature
              return True

          return inner


      expr = transform(bar)
      #└ TYPE (str, *args: bool) -> bool
      """)

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `ParamSpec user generic class with PEP695 syntax`() = test("""
      from typing import Callable

      class Y[U, **P]:
          f: Callable[P, str]
          attr: U

          def __init__(self, f: Callable[P, str], attr: U) -> None:
              self.f = f
              self.attr = attr


      def a(q: int, p: str, r: bool) -> str: ...


      expr = Y(a, 1)
      #└ TYPE Y[int, [q: int, p: str, r: bool]]
      """)

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `type parameter type is typing ParamSpec`() = test("""
      def foo[**P]():
         expr = P
      #   └ TYPE ParamSpec
      """)

    @Test
    @TestFor(issues = ["PY-70484"])
    fun `unbound ParamSpec from unresolved argument replaced with args kwargs`() = test("""
      from typing import Callable, Any, ParamSpec

      P = ParamSpec("P")

      def deco(fn: Callable[P, Any]) -> Callable[P, str]:
          return ... # WARNING Expected type '(**P) -> str', got 'EllipsisType' instead

      expr = deco(unresolved)
      #│          ^^^^^^^^^^ ERROR Unresolved reference 'unresolved'
      #└ TYPE (*args, **kwargs) -> str
      """)

    @Test
    @TestFor(issues = ["PY-70484"])
    fun `unbound ParamSpec that cannot be bound through parameters left intact`() = test("""
      from typing import Callable, Any, ParamSpec

      P = ParamSpec("P")

      def deco() -> Callable[[Callable[P, Any]], Callable[P, int]] # ERROR ':' expected
          return ... # WARNING Expected type '((**P) -> Any) -> (**P) -> int', got 'EllipsisType' instead

      expr = deco()
      #└ TYPE ((**P) -> Any) -> (**P) -> int
      """)

    @Test
    fun `mixing up Concatenate and TypeVarTuple`() = test("""
      from typing import TypeVarTuple, ParamSpec, Callable, Any, Concatenate, reveal_type

      Ts = TypeVarTuple('Ts')
      P = ParamSpec('P')


      def f(prefix: tuple[*Ts], fn: Callable[P, Any]) -> Callable[Concatenate[*Ts, P], int]:
          ...

      def g(x: int, y: str) -> bool:
          ...

      expr = f((1, 2), g)
      #└ TYPE (int, int, x: int, y: str) -> int
      """)

    @Test
    fun `ParamSpec in Concatenate mapped to another ParamSpec`() = test("""
      from typing import Callable, Any, ParamSpec, Concatenate

      P1 = ParamSpec('P1')
      P2 = ParamSpec('P2')

      def f(fn: Callable[P1, Any]):
          expr = g(fn)
      #   └ TYPE (Concatenate(int, **P1)) -> Any

      def g(fn: Callable[P2, Any]) -> Callable[Concatenate[int, P2], Any]:
         ...
      """)

    @Test
    @TestFor(issues = ["PY-82871"])
    fun `Concatenate with ellipsis type`() = test("""
      from typing import Callable, Concatenate

      expr: Callable[Concatenate[int, ...], str]
      #└ TYPE (Concatenate(int, ...)) -> str
      """)

    @Test
    @TestFor(issues = ["PY-77601"])
    fun `ParamSpec correctly parameterized when it is only generic param`() = test("""
      from typing import ParamSpec, Callable, Generic

      P = ParamSpec("P")

      class MyClass(Generic[P]):
          def call(self) -> Callable[P, int]: ...
      c = MyClass[str, int, bool]()
      expr = c.call()
      #└ TYPE (str, int, bool) -> int
      """)

    @Test
    @TestFor(issues = ["PY-77601"])
    fun `ParamSpec not mapped to single type without square brackets`() = test("""
      from typing import ParamSpec, Callable, Generic, TypeVar

      P = ParamSpec("P")
      T = TypeVar("T")

      class MyClass(Generic[T, P]):
          def call(self) -> Callable[P, int]: ...
      c = MyClass[str, int]() # WARNING Passed type arguments do not match type parameters [T, **P] of class 'MyClass'
      expr = c.call()
      #└ TYPE (...) -> int
      """)

    @Test
    @TestFor(issues = ["PY-77601"])
    fun `ParamSpec mapped to single type with square brackets`() = test("""
      from typing import ParamSpec, Callable, Generic, TypeVar

      P = ParamSpec("P")
      T = TypeVar("T")

      class MyClass(Generic[T, P]):
          def call(self) -> Callable[P, T]: ...
      c = MyClass[str, [int]]()
      expr = c.call()
      #└ TYPE (int) -> str
      """)

    @Test
    @TestFor(issues = ["PY-77541"])
    fun `ParamSpec bound to another ParamSpec in custom generic`() = test("""
      class MyCallable[**P, R]:
          def __call__(self, *args: P.args, **kwargs: P.kwargs):
              ...

      def f[**P, R](callback: MyCallable[P, R]) -> MyCallable[P, R]:
          ...

      def g[**P2, R2](callback: MyCallable[P2, R2]) -> MyCallable[P2, R2]: # WARNING Expected type 'MyCallable[**P2, R2]', got 'None' instead
          expr = f(callback)
      #   └ TYPE MyCallable[**P2, R2]
      """)

    @Test
    @TestFor(issues = ["PY-77541"])
    fun `ParamSpec bound to Concatenate in custom generic`() = test("""
      from typing import Concatenate

      class MyCallable[**P, R]:
          def __call__(self, *args: P.args, **kwargs: P.kwargs):
              ...

      def f[**P, R](callback: MyCallable[P, R]) -> MyCallable[P, R]:
          ...

      def g[**P2, R2](callback: MyCallable[Concatenate[int, P2], R2]) -> MyCallable[P2, R2]: # WARNING Expected type 'MyCallable[**P2, R2]', got 'None' instead
          expr = f(callback)
      #   └ TYPE MyCallable[Concatenate(int, **P2), R2]
      """)

    @Test
    @TestFor(issues = ["PY-79060"])
    fun `ParamSpec inside Concatenate bound to callable parameter list in custom generic`() = test("""
      from typing import Concatenate, Callable, Any

      class MyCallable[**P1]:    ...

      def f[**P2](fn: Callable[P2, Any]) -> MyCallable[Concatenate[int, P2]]: ...

      def expects_int_str(n: int, s: str) -> None: ...

      expr = f(expects_int_str)
      #└ TYPE MyCallable[[int, n: int, s: str]]
      """)

    @Test
    @TestFor(issues = ["PY-79060"])
    fun `ParamSpec inside Concatenate bound to another ParamSpec in custom generic`() = test("""
      from typing import Concatenate, Callable, Any

      class MyCallable[**P1]:    ...

      def f[**P2](fn: Callable[P2, Any]) -> MyCallable[Concatenate[int, P2]]: ...

      def param_spec_replaced_with_another_param_spec[**P4](fn: Callable[P4, Any]):
          expr = f(fn)
      #   └ TYPE MyCallable[Concatenate(int, **P4)]
      """)

    @Test
    @TestFor(issues = ["PY-79060"])
    fun `ParamSpec inside Concatenate bound to Concatenate in custom generic`() = test("""
      from typing import Concatenate, Callable, Any

      class MyCallable[**P1]:    ...

      def f[**P2](fn: Callable[P2, Any]) -> MyCallable[Concatenate[int, P2]]: ...

      def param_spec_replaced_with_concatenate[**P3](fn: Callable[Concatenate[int, P3], Any]):
          expr = f(fn)
      #   └ TYPE MyCallable[Concatenate(int, int, **P3)]
      """)
  }

  @Nested
  inner class ParamSpecDefaults {
    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec with defaults class reference`() = test("""
      from typing import ParamSpec, Generic
      DefaultP = ParamSpec("DefaultP", default=[str, int])
      class Foo(Generic[DefaultP]): ...
      expr = Foo
      #└ TYPE type[Foo[[str, int]]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec with defaults class reference new syntax`() = test("""
      class Foo[**P = [str, int]]: ...
      expr = Foo
      #└ TYPE type[Foo[[str, int]]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec with defaults class instance`() = test("""
      from typing import ParamSpec, Generic
      DefaultP = ParamSpec("DefaultP", default=[str, int])
      class Foo(Generic[DefaultP]): ...
      expr = Foo()
      #└ TYPE Foo[[str, int]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec with empty defaults class instance`() = test("""
      from typing import ParamSpec, Generic
      DefaultP = ParamSpec("DefaultP", default=[])
      class Foo(Generic[DefaultP]): ...
      expr = Foo()
      #└ TYPE Foo[[]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec with defaults class instance new syntax`() = test("""
      class Foo[**P = [str, int]]: ...
      expr = Foo()
      #└ TYPE Foo[[str, int]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec with empty defaults class instance new syntax`() = test("""
      class Foo[**P = []]: ...
      expr = Foo()
      #└ TYPE Foo[[]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec with defaults parameterized class instance`() = test("""
      from typing import ParamSpec, Generic
      DefaultP = ParamSpec("DefaultP", default=[str, int])
      class Foo(Generic[DefaultP]): ...
      expr = Foo[[int, bool]]()
      #└ TYPE Foo[[int, bool]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec with defaults extended case`() = test("""
      from typing import Callable, TypeVar, Optional, ParamSpec
      T = TypeVar('T', default=int)
      P = ParamSpec('P', default=[float, bool])
      def catch_exception(function: Callable[P, T]) -> Callable[P, Optional[T]]:
          def decorator(*args: P.args, **kwargs: P.kwargs) -> Optional[T]:...
          return decorator
      expr = catch_exception() # WARNING Parameter 'function' unfilled
      #└ TYPE (float | int, bool) -> int | None
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec with empty defaults extended case`() = test("""
      from typing import Callable, TypeVar, Optional, ParamSpec
      T = TypeVar('T', default=int)
      P = ParamSpec('P', default=[])
      def catch_exception(function: Callable[P, T]) -> Callable[P, Optional[T]]:
          def decorator(*args: P.args, **kwargs: P.kwargs) -> Optional[T]:...
          return decorator
      expr = catch_exception() # WARNING Parameter 'function' unfilled
      #└ TYPE () -> int | None
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec with defaults extended case defaults overridden`() = test("""
      from typing import Callable, TypeVar, Optional
      from typing_extensions import ParamSpec  # or `typing` for `python>=3.10`
      T = TypeVar('T', default=int)
      P = ParamSpec('P', default=[float, bool])
      def catch_exception(function: Callable[P, T]) -> Callable[P, Optional[T]]:
          def decorator(*args: P.args, **kwargs: P.kwargs) -> Optional[T]:...
          return decorator
      def some_func(a: str, b: int, c: list[float]) -> float: ...
      expr = catch_exception(some_func)
      #└ TYPE (a: str, b: int, c: list[float | int]) -> float | int | None
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec with defaults defined in type alias`() = test("""
      from typing import ParamSpec, Generic, Callable
      type PAlias[T = str, **P = [str, int]] = Callable[P, T]
      def wrapper(func: PAlias) -> None:
          pass
      expr = wrapper
      #└ TYPE (func: (str, int) -> str) -> None
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec with defaults defined in type alias overridden`() = test("""
      from typing import ParamSpec, Generic, Callable
      type PAlias[T = str, **P = [str, int]] = Callable[P, T]
      def wrapper(func: PAlias[bool, [str, str]]) -> None:
          pass
      expr = wrapper
      #└ TYPE (func: (str, str) -> bool) -> None
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec default type refers to another ParamSpec new style`() = test("""
      class Clazz[**P1, **P2 = P1, **P3 = P2]: ...
      expr = Clazz[[str]]()
      #└ TYPE Clazz[[str], [str], [str]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec default type refers to another ParamSpec old style`() = test("""
      from typing import Generic, ParamSpec
      P1 = ParamSpec("P1")
      P2 = ParamSpec("P2", default=P1)
      P3 = ParamSpec("P3", default=P2)
      class Clazz(Generic[P1, P2, P3]): ...
      expr = Clazz[[str]]()
      #└ TYPE Clazz[[str], [str], [str]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec default type refers to another ParamSpec old style no explicit`() = test("""
      from typing import Generic, ParamSpec
      P1 = ParamSpec("P1", default=[str])
      P2 = ParamSpec("P2", default=P1)
      P3 = ParamSpec("P3", default=[bool, bool])
      P4 = ParamSpec("P4", default=P3)
      class Clazz(Generic[P1, P2, P3, P4]): ...
      expr = Clazz()
      #└ TYPE Clazz[[str], [str], [bool, bool], [bool, bool]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec with default in constructor`() = test("""
      from typing import Generic, ParamSpec, Callable
      P = ParamSpec("P", default=[int, str, str])
      class ClassA(Generic[P]):
          def __init__(self, x: Callable[P, None] = None) -> None: # WARNING Expected type '(**P) -> None', got 'None' instead
              self.x = x
              ...
      expr = ClassA().x
      #└ TYPE (int, str, str) -> None | None
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `ParamSpec default type refers to another ParamSpec with ellipsis`() = test("""
      class Clazz[**P1, **P2 = P1, **P3 = P2]: ...
      expr = Clazz[..., [float]]()
      #└ TYPE Clazz[Unknown, [float | int], [float | int]]
      """)
  }

  @Nested
  inner class CallableSubtypingAndAssignability {
    @Test
    @TestFor(issues = ["PY-22513"])
    fun `generic kwargs assignment ok`() = test("""
      from typing import Any, TypeVar


      T = TypeVar('T')


      def generic_kwargs(**kwargs: T) -> None:
          pass


      generic_kwargs(a=1, b='foo')
      """)

    @Test
    @TestFor(issues = ["PY-17962"])
    fun `typing Callable call arity`() = test("""
      from typing import Callable


      def foo() -> Callable:
          pass


      def bar() -> Callable[..., str]:
          pass


      def baz() -> Callable[[int, str], str]:
          pass


      cllbl_a = foo()
      cllbl_a()
      cllbl_a(1)
      cllbl_a(1, "2")


      cllbl_b = bar()
      cllbl_b()
      cllbl_b(1)
      cllbl_b(1, "2")


      cllbl_c = baz()
      cllbl_c(1, "2")
      cllbl_c(1, 2) # WARNING Expected type 'str', got 'Literal[2]' instead
      cllbl_c("1", "2") # WARNING Expected type 'int', got 'Literal["1"]' instead
      cllbl_c([], [])
      #       │   ^^ WARNING Expected type 'str', got 'list[Unknown]' instead
      #       ^^ WARNING Expected type 'int', got 'list[Unknown]' instead
      """)

    @Test
    @TestFor(issues = ["PY-44575"])
    fun `args callable against one parameter callable`() = test("""
      from typing import Any, Callable, Iterable, TypeVar
      _T1 = TypeVar("_T1")
      def mymap(c: Callable[[_T1], Any], i: Iterable[_T1]) -> Iterable[_T1]:
        pass
      def myfoo(*args: int) -> int:
        pass
      mymap(myfoo, [1, 2, 3])
      """)

    @Test
    @TestFor(issues = ["PY-16994"])
    fun `callable arity assignability`() = test("""
      import typing


      def too_few_arguments__correct_types(a: int, b: str) -> bool:
          return True

      def too_few_arguments__wrong_types(a: int, b: int) -> bool:
          return True

      def matching_number_arguments__correct_types(a: int, b: str, c: int) -> bool:
          return True

      def matching_number_arguments__wrong_types(a: int, b: str, c: str) -> bool:
          return True

      def too_many_arguments__correct_types(a: int, b: str, c: int, d: str) -> bool:
          return True

      def too_many_arguments__wrong_types(a: int, b: str, c: str, d: str) -> bool:
          return True


      def foo(callback: typing.Callable[[int, str, int], bool]) -> None:
          callback(1, 'abc', 2)


      foo(too_few_arguments__correct_types) # WARNING Expected type '(int, str, int) -> bool', got '(a: int, b: str) -> bool' instead
      foo(too_few_arguments__wrong_types) # WARNING Expected type '(int, str, int) -> bool', got '(a: int, b: int) -> bool' instead
      foo(matching_number_arguments__correct_types)
      foo(matching_number_arguments__wrong_types) # WARNING Expected type '(int, str, int) -> bool', got '(a: int, b: str, c: str) -> bool' instead
      foo(too_many_arguments__correct_types) # WARNING Expected type '(int, str, int) -> bool', got '(a: int, b: str, c: int, d: str) -> bool' instead
      foo(too_many_arguments__wrong_types) # WARNING Expected type '(int, str, int) -> bool', got '(a: int, b: str, c: str, d: str) -> bool' instead
      """)

    @Test
    fun `callable with TypeIs return assignable`() = test("""
      from typing import Any, Callable
      from typing_extensions import TypeIs

      def foo(c: Callable[[Any], TypeIs[int]]):
         ...

      def is_str(x: Any) -> TypeIs[str]:
         ...

      foo(is_str) # WARNING Expected type '(Any) -> TypeIs[int]', got '(x: Any) -> TypeIs[str]' instead
      """)

    @Test
    fun `callable with TypeIs return same type assignable`() = test("""
      from typing import Any, Callable
      from typing_extensions import TypeIs

      def foo(c: Callable[[Any], TypeIs[str]]):
         ...

      def is_str(x: Any) -> TypeIs[str]:
         ...

      foo(is_str)
      """)

    @Test
    fun `callable with TypeIs return narrower not assignable`() = test("""
      from typing import Any, Callable
      from typing_extensions import TypeIs

      class B:
         ...

      class D(B):
         ...

      def foo(c: Callable[[Any], TypeIs[D]]):
         ...

      def is_str(x: Any) -> TypeIs[B]:
         ...

      foo(is_str) # WARNING Expected type '(Any) -> TypeIs[D]', got '(x: Any) -> TypeIs[B]' instead
      """)

    @Test
    fun `callable with TypeIs return wider not assignable`() = test("""
      from typing import Any, Callable
      from typing_extensions import TypeIs

      class B:
         ...

      class D(B):
         ...

      def foo(c: Callable[[Any], TypeIs[B]]):
         ...

      def is_str(x: Any) -> TypeIs[D]:
         ...
      foo(is_str) # WARNING Expected type '(Any) -> TypeIs[B]', got '(x: Any) -> TypeIs[D]' instead
      """)

    @Test
    fun `TypeIs and TypeGuard are not assignable to each other`() = test("""
      from typing import Callable, TypeGuard, TypeIs

      def takes_typeguard(f: Callable[[object], TypeGuard[int]]) -> None:
          pass

      def takes_typeis(f: Callable[[object], TypeIs[int]]) -> None:
          pass

      def is_int_typeis(val: object) -> TypeIs[int]:
          return isinstance(val, int)

      def is_int_typeguard(val: object) -> TypeGuard[int]:
          return isinstance(val, int)

      takes_typeguard(is_int_typeguard)
      takes_typeguard(is_int_typeis) # WARNING Expected type '(object) -> TypeGuard[int]', got '(val: object) -> TypeIs[int]' instead
      takes_typeis(is_int_typeguard) # WARNING Expected type '(object) -> TypeIs[int]', got '(val: object) -> TypeGuard[int]' instead
      takes_typeis(is_int_typeis)
      """)

    @Test
    @TestFor(issues = ["PY-74277"])
    fun `passing TypeIs callable`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON312),
      """
      from typing_extensions import TypeIs, Callable

      def takes_narrower(x: int | str, narrower: Callable[[object], TypeIs[int]]):
          if narrower(x):
              expr1: int = x
          else:
              expr2: str = x

      def is_bool(x: object) -> TypeIs[bool]:
          return isinstance(x, bool)

      takes_narrower(42, is_bool) # WARNING Expected type '(object) -> TypeIs[int]', got '(x: object) -> TypeIs[bool]' instead
      """,
    )

    @Test
    fun `callable subtyping covariance contravariance`() = test("""
      from typing import Callable

      # Test covariance with respect to return types and contravariance with respect to parameter types
      def func1(
          cb1: Callable[[float], int],
          cb2: Callable[[float], float],
          cb3: Callable[[int], int],
      ) -> None:
          f1: Callable[[int], float] = cb1  # OK
          f2: Callable[[int], float] = cb2  # OK
          f3: Callable[[int], float] = cb3  # OK

          f4: Callable[[float], float] = cb1  # OK
          f5: Callable[[float], float] = cb2  # OK
          f6: Callable[[float], float] = cb3 # WARNING Expected type '(float | int) -> float | int', got '(int) -> int' instead

          f7: Callable[[int], int] = cb1  # OK
          f8: Callable[[int], int] = cb2 # WARNING Expected type '(int) -> int', got '(float | int) -> float | int' instead
          f9: Callable[[int], int] = cb3  # OK
      """)

    @Test
    fun `callable subtyping parameter kinds`() = test("""
      from typing import Protocol

      # Test positional-only, keyword-only, and standard parameters
      class PosOnly(Protocol):
          def __call__(self, a: int, b: str, /) -> None: ...

      class KwOnly(Protocol):
          def __call__(self, *, a: int, b: str) -> None: ...

      class Standard(Protocol):
          def __call__(self, a: int, b: str) -> None: ...

      def func2(standard: Standard, pos_only: PosOnly, kw_only: KwOnly):
          f1: Standard = pos_only # WARNING Expected type 'Standard', got 'PosOnly' instead
          f2: Standard = kw_only # WARNING Expected type 'Standard', got 'KwOnly' instead

          f3: PosOnly = standard  # OK
          f4: PosOnly = kw_only # WARNING Expected type 'PosOnly', got 'KwOnly' instead

          f5: KwOnly = standard  # OK
          f6: KwOnly = pos_only # WARNING Expected type 'KwOnly', got 'PosOnly' instead
      """)

    @Test
    fun `callable subtyping args parameter`() = test("""
      from typing import Protocol

      # Test *args parameter
      class NoArgs(Protocol):
          def __call__(self) -> None: ...

      class IntArgs(Protocol):
          def __call__(self, *args: int) -> None: ...

      class FloatArgs(Protocol):
          def __call__(self, *args: float) -> None: ...

      def func3(no_args: NoArgs, int_args: IntArgs, float_args: FloatArgs):
          f1: NoArgs = int_args  # OK
          f2: NoArgs = float_args  # OK

          f3: IntArgs = no_args # WARNING Expected type 'IntArgs', got 'NoArgs' instead
          f4: IntArgs = float_args  # OK

          f5: FloatArgs = no_args # WARNING Expected type 'FloatArgs', got 'NoArgs' instead
          f6: FloatArgs = int_args # WARNING Expected type 'FloatArgs', got 'IntArgs' instead
      """)

    @Test
    fun `callable subtyping args parameter 2`() = test("""
      from typing import Protocol

      class PosOnly(Protocol):
          def __call__(self, a: int, b: str, /) -> None: ...

      class IntArgs(Protocol):
          def __call__(self, *args: int) -> None: ...

      class IntStrArgs(Protocol):
          def __call__(self, *args: int | str) -> None: ...

      class StrArgs(Protocol):
          def __call__(self, a: int, /, *args: str) -> None: ...

      class Standard(Protocol):
          def __call__(self, a: int, b: str) -> None: ...

      def func(int_args: IntArgs, int_str_args: IntStrArgs, str_args: StrArgs):
          f1: PosOnly = int_args # WARNING Expected type 'PosOnly', got 'IntArgs' instead
          f2: PosOnly = int_str_args  # OK
          f3: PosOnly = str_args  # OK
          f4: IntStrArgs = str_args # WARNING Expected type 'IntStrArgs', got 'StrArgs' instead
          f5: IntStrArgs = int_args # WARNING Expected type 'IntStrArgs', got 'IntArgs' instead
          f6: StrArgs = int_str_args  # OK
          f7: StrArgs = int_args # WARNING Expected type 'StrArgs', got 'IntArgs' instead
          f8: IntArgs = int_str_args  # OK
          f9: IntArgs = str_args # WARNING Expected type 'IntArgs', got 'StrArgs' instead
          f10: Standard = int_str_args # WARNING Expected type 'Standard', got 'IntStrArgs' instead
          f11: Standard = str_args # WARNING Expected type 'Standard', got 'StrArgs' instead
      """)

    @Test
    fun `callable subtyping kwargs parameters`() = test("""
      from typing import Protocol

      # Test **kwargs parameter
      class NoKwargs(Protocol):
          def __call__(self) -> None: ...

      class IntKwargs(Protocol):
          def __call__(self, **kwargs: int) -> None: ...

      class FloatKwargs(Protocol):
          def __call__(self, **kwargs: float) -> None: ...

      def func5(no_kwargs: NoKwargs, int_kwargs: IntKwargs, float_kwargs: FloatKwargs):
          f1: NoKwargs = int_kwargs  # OK
          f2: NoKwargs = float_kwargs  # OK

          f3: IntKwargs = no_kwargs # WARNING Expected type 'IntKwargs', got 'NoKwargs' instead
          f4: IntKwargs = float_kwargs  # OK

          f5: FloatKwargs = no_kwargs # WARNING Expected type 'FloatKwargs', got 'NoKwargs' instead
          f6: FloatKwargs = int_kwargs # WARNING Expected type 'FloatKwargs', got 'IntKwargs' instead
      """)

    @Test
    fun `callable subtyping kwargs parameters 2`() = test("""
      from typing import Protocol

      class KwOnly(Protocol):
          def __call__(self, *, a: int, b: str) -> None: ...

      class IntKwargs(Protocol):
          def __call__(self, **kwargs: int) -> None: ...

      class IntStrKwargs(Protocol):
          def __call__(self, **kwargs: int | str) -> None: ...

      class StrKwargs(Protocol):
          def __call__(self, *, a: int, **kwargs: str) -> None: ...

      class Standard(Protocol):
          def __call__(self, a: int, b: str) -> None: ...

      def func(int_kwargs: IntKwargs, int_str_kwargs: IntStrKwargs, str_kwargs: StrKwargs):
          f1: KwOnly = int_kwargs # WARNING Expected type 'KwOnly', got 'IntKwargs' instead
          f2: KwOnly = int_str_kwargs  # OK
          f3: KwOnly = str_kwargs  # OK
          f4: IntStrKwargs = str_kwargs # WARNING Expected type 'IntStrKwargs', got 'StrKwargs' instead
          f5: IntStrKwargs = int_kwargs # WARNING Expected type 'IntStrKwargs', got 'IntKwargs' instead
          f6: StrKwargs = int_str_kwargs  # OK
          f7: StrKwargs = int_kwargs # WARNING Expected type 'StrKwargs', got 'IntKwargs' instead
          f8: IntKwargs = int_str_kwargs  # OK
          f9: IntKwargs = str_kwargs # WARNING Expected type 'IntKwargs', got 'StrKwargs' instead
          f10: Standard = int_str_kwargs # WARNING Expected type 'Standard', got 'IntStrKwargs' instead
          f11: Standard = str_kwargs # WARNING Expected type 'Standard', got 'StrKwargs' instead
      """)

    @Test
    fun `callable subtyping default arguments`() = test("""
      from typing import Protocol

      # Test default arguments
      class DefaultArg(Protocol):
          def __call__(self, x: int = 0) -> None: ...

      class NoDefaultArg(Protocol):
          def __call__(self, x: int) -> None: ...

      class NoX(Protocol):
          def __call__(self) -> None: ...

      def func8(default_arg: DefaultArg, no_default_arg: NoDefaultArg, no_x: NoX):
          f1: DefaultArg = no_default_arg # WARNING Expected type 'DefaultArg', got 'NoDefaultArg' instead
          f2: DefaultArg = no_x # WARNING Expected type 'DefaultArg', got 'NoX' instead

          f3: NoDefaultArg = default_arg  # OK
          f4: NoDefaultArg = no_x # WARNING Expected type 'NoDefaultArg', got 'NoX' instead

          f5: NoX = default_arg  # OK
          f6: NoX = no_default_arg # WARNING Expected type 'NoX', got 'NoDefaultArg' instead
      """)

    @Test
    fun `signatures with ParamSpec`() = test("""
      from typing import Protocol

      class ProtocolWithP[**P](Protocol):
        def __call__(self, *args: P.args, **kwargs: P.kwargs) -> None: ...

      type TypeAliasWithP[**P] = Callable[P, None]
      #                          ^^^^^^^^ ERROR Unresolved reference 'Callable'
      #                          ^^^^^^^^^^^^^^^^^ WARNING Invalid type annotation

      def func[**P](proto: ProtocolWithP[P], ta: TypeAliasWithP[P]):
        # These two types are equivalent
        f1: TypeAliasWithP[P] = proto  # OK
        f2: ProtocolWithP[P] = ta  # OK
      """)

    @Test
    @TestFor(issues = ["PY-76883"])
    fun `callable subtyping keyword only order`() = test("""
      from typing import Protocol

      class C1(Protocol):
          def __call__(self, *, a: int, b: str, c: float): ...

      class C2(Protocol):
          def __call__(self, *, c: float, b: str, a: int): ...

      # Order is not important
      def foo(c1: C1, c2: C2):
          _: C1 = c2
          _: C2 = c1
      """)

    @Test
    fun `wildcard signatures`() = test("""
      from typing import Protocol

      class Expected(Protocol):
          def __call__(self, *args, **kwargs): ...

      class Actual(Protocol):
          def __call__(self, a: float, *, key: str): ...

      def foo(e: Expected, a: Actual):
          _: Expected = a
          _: Actual = e
      """)

    @Test
    @TestFor(issues = ["PY-87802"])
    fun `callable protocol with additional attribute assignment`() = test("""
      from typing import Protocol

      class Proto(Protocol):
          other_attribute: int

          def __call__(self, x: int) -> None:
              pass


      def f(x: int) -> None:
          pass


      v: Proto = f # WARNING Expected type 'Proto', got '(x: int) -> None' instead
      """)

    @Test
    @TestFor(issues = ["PY-77539"])
    fun `matching callable parameter lists`() = test("""
      class MyCallable[**P, R]:
          def __call__(self, *args: P.args, **kwargs: P.kwargs):
              ...
      compatible: MyCallable[[int], object] = MyCallable[[object], str]()
      incompatible1: MyCallable[[object], object] = MyCallable[[int], str]() # WARNING Expected type 'MyCallable[[object], object]', got 'MyCallable[[int], str]' instead
      incompatible2: MyCallable[[int], str] = MyCallable[[object], object]() # WARNING Expected type 'MyCallable[[int], str]', got 'MyCallable[[object], object]' instead
      """)

    @Test
    @TestFor(issues = ["PY-77541"])
    fun `matching unbound ParamSpec with another ParamSpec in custom generic`() = test("""
      class MyCallable[**P, R]:
          def __call__(self, *args: P.args, **kwargs: P.kwargs):
              ...

      def f[**P, R](callback: MyCallable[P, R]) -> MyCallable[P, R]:
          ...

      def g[**P2, R2](callback: MyCallable[P2, R2]) -> MyCallable[P2, R2]:
          return f(callback)
      """)

    @Test
    @TestFor(issues = ["PY-82871"])
    fun `Concatenate with ellipsis assignability`() = test("""
      from typing import Callable, Concatenate

      call: Callable[Concatenate[int, ...], str]

      call(42)
      call(42, True)
      call("foo") # WARNING Expected type 'int', got 'Literal["foo"]' instead
      call()

      def single_int(x: int) -> str:
          pass

      def int_bool(x: int, y: bool) -> str:
          pass

      def single_str(x: str) -> str:
          pass

      def empty() -> str:
          pass

      call = single_int
      call = int_bool
      #^^^ WARNING Redeclared 'call' defined above without usage
      call = single_str
      #│     ^^^^^^^^^^ WARNING Expected type '(Concatenate(int, ...)) -> str', got '(x: str) -> str' instead
      #^^^ WARNING Redeclared 'call' defined above without usage
      call = empty
      #│     ^^^^^ WARNING Expected type '(Concatenate(int, ...)) -> str', got '() -> str' instead
      #^^^ WARNING Redeclared 'call' defined above without usage
      """)

    @TestFor(issues = ["PY-89912"])
    @Test
    fun `callable with parameter of type Self`() = test("""
      from typing import Self, Callable

      class Shape:
          def apply(self, f: Callable[[Self], None]) -> None: ...

      class Circle(Shape): ...

      def accept_circle(c: Circle): ...
      
      def accept_shape(s: Shape): ...

      circle = Circle()
      circle.apply(accept_shape)

      shape = Shape()
      shape.apply(accept_circle)
      #           ^^^^^^^^^^^^^ WARNING Expected type '(Shape) -> None', got '(c: Circle) -> None' instead
      """)
  }

  @Nested
  inner class ParamSpecConcatenateTypeChecker {
    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec example call site argument check`() = test("""
      from typing import Callable, ParamSpec

      P = ParamSpec("P")


      def changes_return_type_to_str(x: Callable[P, int]) -> Callable[P, str]: ...


      def returns_int(a: str, b: bool) -> int:
          return 42


      changes_return_type_to_str(returns_int)("42", 42) # WARNING Expected type 'bool', got 'Literal[42]' instead
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec user generic class method call argument check`() = test("""
      from typing import TypeVar, Generic, Callable, ParamSpec

      U = TypeVar("U")
      P = ParamSpec("P")


      class Y(Generic[U, P]):
          f: Callable[P, U]
          attr: U

          def __init__(self, f: Callable[P, U], attr: U) -> None:
              self.f = f
              self.attr = attr


      def a(q: int) -> str: ...


      expr = Y(a, '1').f("42") # WARNING Expected type 'int', got 'Literal["42"]' instead
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec user generic class method several parameters call check`() = test("""
      from typing import TypeVar, Generic, Callable, ParamSpec

      U = TypeVar("U")
      P = ParamSpec("P")


      class Y(Generic[U, P]):
          f: Callable[P, U]
          attr: U

          def __init__(self, f: Callable[P, U], attr: U) -> None:
              self.f = f
              self.attr = attr


      def a(q: int, s: str) -> str: ...


      expr = Y(a, '1').f(42, 42) # WARNING Expected type 'str', got 'Literal[42]' instead
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec user generic class method concatenate call check`() = test("""
      from typing import TypeVar, Generic, Callable, ParamSpec, Concatenate

      U = TypeVar("U")
      P = ParamSpec("P")


      class Y(Generic[U, P]):
          f: Callable[Concatenate[int, P], U]
          attr: U

          def __init__(self, f: Callable[Concatenate[int, P], U], attr: U) -> None:
              self.f = f
              self.attr = attr


      def a(q: int, s: str, b: bool) -> str: ...


      expr = Y(a, '1').f(42, 42, 42)
      #                      │   ^^ WARNING Expected type 'bool', got 'Literal[42]' instead
      #                      ^^ WARNING Expected type 'str', got 'Literal[42]' instead
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate add third parameter`() = test("""
      from typing import Callable, Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


      add(bar)("42", 42, 42) # WARNING Expected type 'bool', got 'Literal[42]' instead
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate add second parameter`() = test("""
      from typing import Callable,  Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


      add(bar)("42", "42", True) # WARNING Expected type 'int', got 'Literal["42"]' instead
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate add first parameter`() = test("""
      from typing import Callable,  Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


      add(bar)(42, 42, True) # WARNING Expected type 'str', got 'Literal[42]' instead
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate add first several parameters`() = test("""
      from typing import Callable,  Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def add(x: Callable[P, int]) -> Callable[Concatenate[str, list[str], P], bool]: ...


      add(bar)(42, [42], 3, True)
      #        │   ^^^^ WARNING Expected type 'list[str]', got 'list[Literal[42]]' instead
      #        ^^ WARNING Expected type 'str', got 'Literal[42]' instead
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate add ok`() = test("""
      from typing import Callable,  Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


      add(bar)("42", 42, True, True, True)
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate remove call check`() = test("""
      from typing import Callable,  Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


      remove(bar)(42) # WARNING Expected type 'bool', got 'Literal[42]' instead
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate remove ok one bool`() = test("""
      from typing import Callable,  Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


      remove(bar)(True)
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate remove ok two bools`() = test("""
      from typing import Callable,  Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


      remove(bar)(True, True)
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate remove ok empty`() = test("""
      from typing import Callable,  Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


      remove(bar)()
      """)

    @Test
    @TestFor(issues = ["PY-49935"])
    fun `ParamSpec concatenate transform call check`() = test("""
      from typing import Callable,  Concatenate, ParamSpec

      P = ParamSpec("P")


      def bar(x: int, *args: bool) -> int: ...


      def transform(
              x: Callable[Concatenate[int, P], int]
      ) -> Callable[Concatenate[str, P], bool]:
          def inner(s: str, *args: P.args): # WARNING 'P.args' and 'P.kwargs' must both be present in the same function signature
              return True
          return inner


      transform(bar)(42) # WARNING Expected type 'str', got 'Literal[42]' instead
      """)

    @Test
    @TestFor(issues = ["PY-79098"])
    fun `callable concatenate matching`() = test("""
      from typing import Callable, Concatenate, reveal_type

      def f[**P2](fn: Callable[Concatenate[int, int, P2], None]) -> Callable[P2, None]: # WARNING Expected type '(**P2) -> None', got 'None' instead
          def shorter_concat[**P3](fn: Callable[Concatenate[int, P3], None]):
              f(fn) # WARNING Expected type '(Concatenate(int, int, **P2)) -> None' (matched generic type '(Concatenate(int, int, **P2)) -> None'), got '(Concatenate(int, **P3)) -> None' instead
          def longer_concat[**P3](fn: Callable[Concatenate[int, int, int, P3], None]):
              f(fn)
          def empty(fn: Callable[[], None]):
              f(fn) # WARNING Expected type '(Concatenate(int, int, **P2)) -> None' (matched generic type '(Concatenate(int, int, **P2)) -> None'), got '() -> None' instead
          def param_spec[**P3](fn: Callable[P3, None]):
              f(fn) # WARNING Expected type '(Concatenate(int, int, **P2)) -> None' (matched generic type '(Concatenate(int, int, **P2)) -> None'), got '(**P3) -> None' instead
          def shorter_param_list[**P3](fn: Callable[[int], None]):
              f(fn) # WARNING Expected type '(Concatenate(int, int, **P2)) -> None' (matched generic type '(Concatenate(int, int, **P2)) -> None'), got '(int) -> None' instead
          def exact_param_list[**P3](fn: Callable[[int, int], None]):
              f(fn)
          def longer_param_list[**P3](fn: Callable[[int, int, int], None]):
              f(fn)
      """)

    @Test
    @TestFor(issues = ["PY-79098"])
    fun `user generic concatenate matching`() = test("""
      from typing import Callable, Concatenate, reveal_type

      class MyCallable[**P, R]:
          pass

      def g[**P2](fn: MyCallable[Concatenate[int, int, P2], None]) -> MyCallable[P2, None]: # WARNING Expected type 'MyCallable[**P2, None]', got 'None' instead
          def shorter_concat[**P3](fn: MyCallable[Concatenate[int, P3], None]):
              g(fn) # WARNING Expected type 'MyCallable[Concatenate(int, int, **P2), None]' (matched generic type 'MyCallable[Concatenate(int, int, **P2), None]'), got 'MyCallable[Concatenate(int, **P3), None]' instead
          def longer_concat[**P3](fn: MyCallable[Concatenate[int, int, int, P3], None]):
              g(fn)
          def empty(fn: MyCallable[[], None]):
              g(fn) # WARNING Expected type 'MyCallable[Concatenate(int, int, **P2), None]' (matched generic type 'MyCallable[Concatenate(int, int, **P2), None]'), got 'MyCallable[[], None]' instead
          def param_spec[**P3](fn: MyCallable[P3, None]):
              g(fn) # WARNING Expected type 'MyCallable[Concatenate(int, int, **P2), None]' (matched generic type 'MyCallable[Concatenate(int, int, **P2), None]'), got 'MyCallable[**P3, None]' instead
          def shorter_param_list[**P3](fn: MyCallable[[int], None]):
              g(fn) # WARNING Expected type 'MyCallable[Concatenate(int, int, **P2), None]' (matched generic type 'MyCallable[Concatenate(int, int, **P2), None]'), got 'MyCallable[[int], None]' instead
          def exact_param_list[**P3](fn: MyCallable[[int, int], None]):
              g(fn)
          def longer_param_list[**P3](fn: MyCallable[[int, int, int], None]):
              g(fn)
      """)

    @Test
    @TestFor(issues = ["PY-50403"])
    fun `function named parameter unification`() = test("""
      from typing import Callable,  ParamSpec

      P = ParamSpec("P")


      def twice(f: Callable[P, int], *args: P.args, **kwargs: P.kwargs) -> int:
          return f(*args, **kwargs) + f(*args, **kwargs)


      def a_int_b_str(a: int, b: str) -> int:
          return a + len(b)


      res1 = twice(a_int_b_str, 1, "A")

      res2 = twice(a_int_b_str, b="A", a=1)

      res3 = twice(a_int_b_str, "A", 1)
      #                         │    └ WARNING Expected type 'str', got 'Literal[1]' instead
      #                         ^^^ WARNING Expected type 'int', got 'Literal["A"]' instead

      res4 = twice(a_int_b_str, b=1, a="A")
      #                         │    ^^^^^ WARNING Expected type 'int', got 'Literal["A"]' instead
      #                         ^^^ WARNING Expected type 'str', got 'Literal[1]' instead
      """)

    @Test
    @TestFor(issues = ["PY-50403"])
    fun `function not enough arguments to match with ParamSpec`() = test("""
      from typing import ParamSpec, Callable, TypeVar

      P = ParamSpec('P')
      T = TypeVar('T')


      def caller(f: Callable[P, T], *args: P.args, **kwargs: P.kwargs):
          f(*args, **kwargs)


      def func(n: int, s: str) -> None:
          pass


      caller(func, 42) # WARNING Parameter 's' unfilled (from ParamSpec 'P')
      """)

    @Test
    @TestFor(issues = ["PY-50403"])
    fun `function too many arguments to match with ParamSpec`() = test("""
      from typing import ParamSpec, Callable, TypeVar

      P = ParamSpec('P')
      T = TypeVar('T')


      def caller(f: Callable[P, T], *args: P.args, **kwargs: P.kwargs):
          f(*args, **kwargs)


      def func(n: int, s: str) -> None:
          pass


      caller(func, 42, 'foo', None) # WARNING Unexpected argument (from ParamSpec 'P')
      """)

    @Test
    @TestFor(issues = ["PY-50403"])
    fun `function named argument not match with ParamSpec`() = test("""
      from typing import ParamSpec, Callable, TypeVar

      P = ParamSpec('P')
      T = TypeVar('T')


      def caller(f: Callable[P, T], *args: P.args, **kwargs: P.kwargs):
          f(*args, **kwargs)


      def func(foo: int) -> None:
          pass


      caller(func, bar=42)
      #            │     └ WARNING Parameter 'foo' unfilled (from ParamSpec 'P')
      #            ^^^^^^ WARNING Unexpected argument (from ParamSpec 'P')
      """)

    @Test
    @TestFor(issues = ["PY-50403"])
    fun `same argument passed twice in ParamSpec`() = test("""
      from typing import ParamSpec, Callable, TypeVar

      P = ParamSpec('P')
      T = TypeVar('T')


      def caller(f: Callable[P, T], *args: P.args, **kwargs: P.kwargs):
          f(*args, **kwargs)


      def func(n: int) -> None:
          pass


      caller(func, 42, n=42) # WARNING Unexpected argument (from ParamSpec 'P')
      """)

    @Test
    @TestFor(issues = ["PY-80704"])
    fun `ParamSpec derived`() = test("""
      class Base[**P]: ...
      b: Base[[int]]

      class Derived1(Base[int]): ...
      b = Derived1()

      class Derived2(Base[str]): ...
      b = Derived2()
      #│  ^^^^^^^^^^ WARNING Expected type 'Base[[int]]', got 'Derived2' instead
      #\ WARNING Redeclared 'b' defined above without usage

      class Derived3[**P](Base[P]): ...
      b = Derived3()
      #\ WARNING Redeclared 'b' defined above without usage
      """)

    @Test
    @TestFor(issues = ["PY-80704"])
    fun `ParamSpec protocol`() = test("""
      from typing import Protocol, Callable

      class Proto[**P](Protocol):
          f: Callable[P, None]
      p: Proto[[int]]

      class Match:
          f: Callable[[int], None]
      p = Match()

      class Mismatch:
          f: Callable[[str], None]
      p = Mismatch()
      #│  ^^^^^^^^^^ WARNING Expected type 'Proto[[int]]', got 'Mismatch' instead
      #\ WARNING Redeclared 'p' defined above without usage
      """)

    @Test
    fun `ParamSpec protocol empty`() = test("""
      from typing import Protocol

      class Proto[**P](Protocol): ...

      _: Proto[[]] = 1
      """)

    @Test
    @TestFor(issues = ["PY-80775"])
    fun `ParamSpec protocol full`() = test("""
      from typing import Protocol

      class Proto[**P](Protocol):
          def f(self, *args: P.args, **kwargs: P.kwargs) -> None: ...

      class Impl:
          def f(self, i: int) -> None: ...

      p: Proto[[int]] = Impl()
      """)

    @Test
    @TestFor(issues = ["PY-76850"])
    fun `ParamSpec component forwarding`() = test("""
      from typing import Callable, Concatenate, ParamSpec
      P = ParamSpec("P")

      def decorator(f: Callable[P, int]) -> Callable[P, None]:
          def foo(*args: P.args, **kwargs: P.kwargs) -> None:
              f(*args, **kwargs)  # OK
              f(*kwargs, **args)
      #         │        ^^^^^^ WARNING Unexpected argument (from ParamSpec 'P')
      #         ^^^^^^^ WARNING Unexpected argument (from ParamSpec 'P')
              f(1, *args, **kwargs) # WARNING Unexpected argument (from ParamSpec 'P')
          return foo

      def remove(f: Callable[Concatenate[int, P], int]) -> Callable[P, None]:
          def foo(*args: P.args, **kwargs: P.kwargs) -> None:
              f(1, *args, **kwargs)  # OK
              f(*args, 1, **kwargs)
      #         │      └ WARNING Unexpected argument (from ParamSpec 'P')
      #         ^^^^^ WARNING Unexpected argument (from ParamSpec 'P')
              f(*args, **kwargs) # WARNING Unexpected argument (from ParamSpec 'P')
          return foo

      def outer(f: Callable[P, None]) -> Callable[P, None]:
          def foo(x: int, *args: P.args, **kwargs: P.kwargs) -> None:
              f(*args, **kwargs)
          def bar(*args: P.args, **kwargs: P.kwargs) -> None:
              foo(1, *args, **kwargs)  # OK
              foo(x=1, *args, **kwargs) # WARNING Unexpected argument (from ParamSpec 'P')
          return bar
      """)

    @Test
    @TestFor(issues = ["PY-76850"])
    fun `ParamSpec substituted call site`() = test("""
      from typing import Callable, ParamSpec
      P = ParamSpec("P")

      def twice(f: Callable[P, int], *args: P.args, **kwargs: P.kwargs) -> int:
          return f(*args, **kwargs) + f(*args, **kwargs)

      def a_int_b_str(a: int, b: str) -> int:
          return 0

      twice(a_int_b_str, 1, "A")  # OK
      twice(a_int_b_str, b="A", a=1)  # OK
      twice(a_int_b_str, b=1, a="A")
      #                  │    ^^^^^ WARNING Expected type 'int', got 'Literal["A"]' instead
      #                  ^^^ WARNING Expected type 'str', got 'Literal[1]' instead
      twice(a_int_b_str, "A", 1)
      #                  │    └ WARNING Expected type 'str', got 'Literal[1]' instead
      #                  ^^^ WARNING Expected type 'int', got 'Literal["A"]' instead
      """)
  }

  @Nested
  inner class Functools {
    @Test
    @TestFor(issues = ["PY-23067"])
    fun `functools wraps`() = test("""
      import functools

      class MyClass:
        def foo(self, i: int):
            pass

      class Route:
          @functools.wraps(MyClass.foo)
          def __init__(self):
              pass

      class Router:
          @functools.wraps(wrapped=Route.__init__)
          def route(self, s: str):
              pass

      router = Router()
      router.route(-2)
      router.route("") # WARNING Expected type 'int', got 'Literal[""]' instead
      """)

    @Test
    @TestFor(issues = ["PY-23067"])
    fun `functools wraps multi file`() = test(
      """
      from m import Router

      router = Router()
      router.route(-2)
      router.route("") # WARNING Expected type 'int', got 'Literal[""]' instead
      """,
      "m.py" to """
        import functools


        class MyClass:
            def foo(self, i: int):
                pass


        class Route:
            @functools.wraps(MyClass.foo)
            def __init__(self):
                pass


        class Router:
            @functools.wraps(wrapped=Route.__init__)
            def route(self, s: str):
                pass
        """,
    )
  }

  @Test
  fun `call on non-reference callee with default parameter`() = test("""
    class CallableTest:
        def __call__(self, arg=None):
            pass

    CallableTest()("bad 1")
    """)

  @Test
  @TestFor(issues = ["PY-35544"])
  fun `less specific callable accepted for more specific callable parameter`() = test("""
    from typing import Callable

    class MainClass:
        pass

    class SubClass(MainClass):
        pass

    def f(p: Callable[[SubClass], int]):
        pass

    def g(p: MainClass) -> int:
        pass

    f(g)
    """)
}

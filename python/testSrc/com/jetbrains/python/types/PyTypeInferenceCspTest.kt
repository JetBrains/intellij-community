// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.types.CspBuilder
import com.jetbrains.python.psi.types.PyTypeInferenceCspFactory
import org.junit.jupiter.api.Test


@TestFor(classes = [PyTypeInferenceCspFactory::class, CspBuilder::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyTypeInferenceCspTest : PyCodeInsightTestCase() {

  @Test
  fun `Simple union via arguments`() = test("""
    def bar[U](a: U, b: U) -> U:
      return None

    r1 = bar(1, "s")
    #└ TYPE int | str
    """.trimIndent())

  @Test
  fun `Constraint solver simple 2`() = test("""
    class A(): ...
    class B(A): ...
    class C(A): ...

    def bar[U: A|None](a: U, b: U) -> U:
      return None

    r1 = bar(B(), C())
    #└ TYPE B | C
    """.trimIndent())

  @Test
  fun `Constraint solver simple 3`() = test("""
    class A(): ...
    class B(A): ...

    def bar[U: (A,str)](a: U) -> U:
      return ""

    r1 = bar(B())
    #└ TYPE A
    """.trimIndent())

  @Test
  fun `TV with constraints 1`() = test("""
    def compile[AnyStr: (str, int)](pattern: AnyStr) -> list[AnyStr]: ...

    res = compile("s")
    # └ TYPE list[str]
    """.trimIndent())

  @Test
  fun `Unconstrained TV with default 1`() = test("""
    def f[T=int]() -> T: ...

    f()
    # └ TYPE int
    """.trimIndent())

  @Test
  fun `Unconstrained TV with default 2`() = test("""
    def f[T=str](p: int | list[T]) -> T: ...

    f(3)
    #  └ TYPE str
    f([True])
    #       └ TYPE bool
    """.trimIndent())

  @Test
  fun `Empty tuple`() = test("""
    from typing import Sequence

    def test_seq[T](x: Sequence[T]) -> Sequence[T]:
      return x

    def func8(t3: tuple[()]):
      test_seq(t3)
    #            └ TYPE Sequence[Never]
    """.trimIndent())

  @Test
  @TestCaseOptions(
    assertRecursionPrevention = false,
    additionalSdkRoots = [SdkRoot("packages", OrderRootTypeEnum.CLASSES)],
  )
  fun `Attrs type per default`() = test("""
    from typing import Any, assert_type
    import attr

    @attr.s
    class B1:
      x = attr.ib()
      y = attr.ib(default=0)
      z = attr.ib(default=attr.Factory(list))

    def f(b1: B1) :
      b1.y
    #    └ TYPE int
      b1.z
    #    └ TYPE list FIXME list[Any] # PY-88142
    """.trimIndent())

  @Test
  fun `Type per overload matching`() = test("""
    from typing import Any, overload

    class Class5[T]:
      @overload
      def __init__(self: "Class5[list[int]]", value: int) -> None: ...
      @overload
      def __init__(self: "Class5[set[str]]", value: str) -> None: ...
      @overload
      def __init__(self, value: T) -> None:
          pass

      def __init__(self, value: Any) -> None:
        pass


    Class5(0)
    #       └ TYPE Class5[list[int]]
    Class5("")
    #        └ TYPE Class5[set[str]]
    """.trimIndent())

  @Test
  fun `Bound from return to argument`() = test("""
    from typing import Callable, Any

    def fooFun[U](f: Callable[[U], Any]) -> U:
      return None

    r0 : str = fooFun(lambda p: p) # currently p is U
    #                           └ TYPE str
    """.trimIndent())

  @Test
  fun `Match union bound 0`() = test("""
    from typing import Callable

    def f2[T](arg: Callable[[T], None]) -> T:
      pass

    def callback(p: int) : ...

    r0 = f2(callback)
    #└ TYPE int
    """.trimIndent())

  @Test
  fun `Match union bound 1`() = test("""
    from typing import Callable

    def f2[T](arg: str | Callable[[T], None]) -> T:
      pass

    def callback(p: int) : ...

    r0 = f2(callback)
    #└ TYPE int
    """.trimIndent())

  @Test
  fun `Match union bound 2`() = test("""
    from typing import Any, Callable, Literal

    def f2[T: int | Callable[[str], int]](arg: T) -> T:
      pass

    my_lambda = lambda s,/: 42
    r0 = f2(my_lambda)
    #└ TYPE (s: Unknown, /) -> Literal[42]
    """.trimIndent())

  @Test
  fun `Match union bound 3`() = test("""
    from typing import Callable

    def f2[T: int | Callable[[str], int]](arg: T) -> T:
      pass

    def callback(p: str, /) -> int : ...

    r = f2(callback)
    #\ TYPE (p: str, /) -> int
    """.trimIndent())

  @Test
  fun `Match union bound 4`() = test("""
    from typing import Any, Callable

    def f2[T=bool](arg: T | Callable[[str], int]) -> T:
      pass

    r = f2(lambda s: 42)
    #\ TYPE bool
    """.trimIndent())

  @Test
  fun `Match constraint`() = test("""
    from typing import Any, Callable

    def f2[T: (int, Callable[[str], str])](arg: T) -> T:
      pass

    r = f2(lambda s: s)
    #\ TYPE (str) -> str
    """.trimIndent())

  @Test
  fun `Nested type variables 4a`() = test("""
    class A: ...
    class B(A): ...
    class Pair[U, V]:
      def __init__(self, first: U, second: V):
        self.first = first
        self.second = second

    def merge[M](pair: Pair[M, M]) -> M:
      return None

    def pipe[P](arg: P) -> P:
      return None

    r4a = merge(pipe(Pair(B(), A()))) # note: same result without call to 'pipe'
    # └ TYPE B | A
    """.trimIndent())

  @Test
  fun `Nested type variables 4b`() = test("""
    class A: ...
    class B(A): ...
    class Pair[U, V]:
      def __init__(self, first: U, second: V):
        self.first = first
        self.second = second

    def merge[U](pair: Pair[U, U]) -> U:
      return None

    r4b = merge(Pair("s", 1))
    # └ TYPE str | int
    """.trimIndent())

  @Test
  fun `Nested type variables 4c`() = test("""
    class A: ...
    class B(A): ...
    class Pair[U, V]:
      def __init__(self, first: U, second: V):
        self.first = first
        self.second = second

    def merge2[U, V](pair1: Pair[U, V], pair2: Pair[U, V]) -> U:
      return None

    r4c = merge2(Pair(B(), B()), Pair(B(), A()))
    # └ TYPE B
    """.trimIndent())

  @Test
  fun `Generic and self`() = test("""
    class A:
        def copy[T](self: T) -> T:
            return self

    A.copy(A())
    #         └ TYPE A
    """.trimIndent())

  @Test
  fun `Deeply nested generics`() = test("""
    from typing import Literal

    def f[T](x: list[list[list[T]]]) -> T:
        ...

    res = f([[[1]]])
    #              └ TYPE int
    """.trimIndent())

  @Test
  fun `Type var constraints vs bound`() = test("""
    def f_constrained[T: (int, str)](x: T) -> T:
        return x

    def f_bound[T: int](x: T) -> T:
        return x

    r1 = f_constrained(1)
    #└ TYPE int

    r2 = f_constrained("s")
    #└ TYPE str

    class MyInt(int): ...
    r3 = f_bound(MyInt())
    #└ TYPE MyInt

    r4 = f_constrained(MyInt())
    #└ TYPE int
    """.trimIndent())

  @Test
  fun `Handle inferred intersections 1`() = test("""
    from typing import Callable, TypeVar

    class A: ...
    class B: ...
    class C(B, A): ...

    T = TypeVar('T', bound=A)

    def func(c: Callable[[T], None])->T:
        pass

    def accepts_str(x: B) -> None:
        pass

    res: C = func(accepts_str)
    # └ TYPE C
    """.trimIndent())

  @Test
  fun `Handle inferred intersections 2`() = test("""
    from typing import Callable, TypeVar, Never, Any

    class A: ...
    class B: ...
    # class C(B, A): ... # no C given

    T = TypeVar('T', bound=A)

    def func(c: Callable[[T], None])->T:
        pass

    def accepts_str(x: B) -> None:
        pass

    res = func(accepts_str)
    # │        ^^^^^^^^^^^ WARNING Expected type '(T ≤: A) -> None', got '(x: B) -> None' instead
    # └ TYPE Unknown
    """.trimIndent())

  @Test
  fun `Infer constrained type`() = test("""
    from typing import TypeVar, Generic

    class A: ...
    class B: ...
    class C(B): ...

    T = TypeVar("T", A, B, contravariant=True)
    #                      ^^^^^^^^^^^^^^^^^^ WARNING Superfluous variance since the given constraints have no subtype relation

    class Box(Generic[T]):
        def __init__(self, t: T):
            pass

    a = Box(C())
    #\ TYPE Box[B]
    """.trimIndent())

  @Test
  fun `Handle raw generic type`() = test("""
    from typing import overload
    class A: ...
    class B(A): ...
    class Box[E:A]:
        @overload
        def __init__(self): ...
        @overload
        def __init__(self, e: E | None): ...
        def __init__(self, e: E | None = None): ...

    def foo[U:Box](u:U) -> U:
        pass

    foo(Box(A()))
    foo(Box())
    b1: Box[A] = Box()
    b2: Box[B] = Box()
    """.trimIndent())

  @TestFor(issues = ["PY-86098"])
  @Test
  fun `PY-86098`() = test("""
    class A[T: object]:
        def __init__(self, value: T): ...

    a1 = A(1)
    #└ TYPE A[int]
    """.trimIndent())

  @TestFor(issues = ["PY-87890"])
  @Test
  fun `Nested csp with type parameter bound`() = test("""
    from typing import Literal

    from typing import Any, Callable

    def f[F: Callable[..., Any]]() -> Callable[[F], F]:
        return lambda x: x

    res = f()(lambda x: 1)(1)
    # └ TYPE Literal[1]
    """.trimIndent())

  @Test
  fun `Nested csp with type parameter default`() = test("""
    def f[T = str](*args: T) -> T: ...

    f(2)
    #  └ TYPE int
    f()
    # └ TYPE str
    """.trimIndent())

  @Test
  fun `Nested csp with type parameter default Any`() = test("""
    from typing import Any

    def f[T = Any](*args: T) -> T: ...

    f(2)
    #  └ TYPE int
    f()
    # └ TYPE Any
    """.trimIndent())

  @Test
  fun `Nested csp with type parameter constraint`() = test("""
    from typing import Callable, Any

    def f[T : (str, int)]() -> Callable[[T], T]: ...

    f()(2)
    #    └ TYPE int
    f()("s")
    #      └ TYPE str
    """.trimIndent())

  @Test
  fun `Keep unconstrained type parameters for type return`() = test("""
    from typing import Generic, TypeVar

    T = TypeVar("T", infer_variance=False)

    class Box(Generic[T]):
        ...

    def box_class() -> type[Box[T]]:
        return Box

    C = box_class()
    box_int : Box[int] = C()
    #  └ TYPE Box[int]
    """.trimIndent())

  @TestFor(issues = ["PY-89826"])
  @Test
  fun `Performance and healthy termination on nested inference variable`() = test("""
    from typing import TypeVar

    K = TypeVar("K")
    V = TypeVar("V")

    class MultiDict(dict[K, V]):
        def deepcopy(self) -> dict[K, list[V]]:
            return self.to_dict()

        def to_dict(self) -> dict[K, V]: ...
    """.trimIndent())

  @TestFor(issues = ["PY-88071"])
  @Test
  fun `Default type from nested call`() = test("""
    def h1[S=int]() -> S: ...
    def h2[T](t: T) -> T: ...
    rh2 = h2(h1())
    # └ TYPE int
    """.trimIndent())

  @TestFor(issues = ["PY-88071"])
  @Test
  fun `Default type from outer call`() = test("""
    def g1[S]() -> S: ...
    def g2[T=str](t: T) -> T: ...
    rg2 = g2(g1())
    # └ TYPE S
    """.trimIndent())

  @TestFor(issues = ["PY-88071"])
  @Test
  fun `Default type from both calls`() = test("""
    def f1[S=int]() -> S: ...
    def f2[T=str](t: T) -> T: ...
    rf2 = f2(f1())
    # └ TYPE int
    """.trimIndent())

  @TestFor(issues = ["PY-88071"])
  @Test
  fun `Default type from both calls explicit Any`() = test("""
    from typing import Any
    def f1[S=Any]() -> S: ...
    def f2[T=str](t: T) -> T: ...
    rf2 = f2(f1())
    # └ TYPE Any
    """.trimIndent())

  @TestFor(issues = ["PY-88089"])
  @Test
  fun `Keep captured type`() = test("""
    def f[S](s: S) -> S: ...
    def main[T = int](t: T) -> T:
        r = f(t)
    #   └ TYPE T
    """.trimIndent())

  @TestFor(issues = ["PY-88696"])
  @Test
  fun `Error when incorrect type in generic function`() = test("""
    def f[T](t: T) -> T: ...

    a: str = f(1)
    #        ^^^^ WARNING Expected type 'str', got 'int' instead
    """.trimIndent())

  @TestFor(issues = ["PY-89047"])
  @Test
  fun `Empty list literal should not collapse generic unification to Any`() = test("""
    from typing import Literal

    def foo[T](_0: list[T], _1: list[T]) -> T:
        raise NotImplementedError

    foo([1], [])
    #          └ TYPE int
    foo([], [1])
    #          └ TYPE int
    """.trimIndent())

  @TestFor(issues = ["PY-90270"])
  @Test
  fun `Contravariant type argument unifies to the common subtype`() = test("""
    class Sink[T]:
        def put(self, t: T) -> None: ...

    def f[U](a: Sink[U], b: Sink[U]) -> U: ...

    res = f(Sink[object](), Sink[int]())
    # └ TYPE int
    """.trimIndent())

  @TestFor(issues = ["PY-89862"])
  @Test
  fun `Type inference of list literal respects expected type 1`() = test("""
    data: list[int | None] = [None] * 42 # expect no error: Expected type 'list[int | None]', got 'list[None]' instead
    """.trimIndent())

  @TestFor(issues = ["PY-90086"])
  @Test
  fun `Type inference of list literal respects expected type 2`() = test("""
    def foo(lst: list[list[int | str]]) -> None: ...
    foo([[1], ["a"]]) # expect no error
    """.trimIndent())

  @TestFor(issues = ["PY-90097"])
  @Test
  fun `Cannot assign list comprehension with enum member to a variable expecting a list of this enum`() = test("""
    from enum import Enum

    class Tile(Enum):
        EMPTY = 0
        WALL = 1

    foo: list[list[Tile]] = [[Tile.EMPTY for _ in range(5)] for _ in range(5)] # expect no error
    bar: list[Tile] = [Tile.EMPTY for _ in range(5)] # expect no error
    """.trimIndent())

  @TestFor(issues = ["PY-90463"])
  @Test
  fun `Widen list literal 'abc' when expected`() = test("""
    def f(x: list[str] | int): ...
    f(["abc"]) # expect no error
    """.trimIndent())

  @TestFor(issues = ["PY-90472"])
  @Test
  fun `Nested list-literal type is too harshly resolved to a nested list of literal element types`() = test("""
    def foo(param: list[list[int]]) -> None: ...
    foo([[0, 2, 5], [0, 1, 2], [1, 2, 1], [3, 0, 3]]) # expect no error
    """.trimIndent())

  @TestFor(issues = ["PY-88624"])
  @Test
  fun `Overloaded init self type overrides default type parameter`() = test("""
    from typing import overload

    class Class[T=list[int] | None]:
        @overload
        def __init__(self: Class[None]) -> None: ...

        @overload
        def __init__(self, x: T) -> None: ...

        def __init__(self, *args, **kwargs) -> None: ...

    Class()
    #     └ TYPE Class[None]
    Class(1)
    #      └ TYPE Class[int]
    """.trimIndent())

  @TestFor(issues = ["PY-91194"])
  @Test
  fun `Performance of combination of overloads and control flow and csp`() = test("""
    class Solution:
        def maxProduct(self, nums: list[int]) -> int:
            result = max(nums)
    
            cur_max = 1
            cur_min = 1
    
            for x in nums:
                if x == 0:
                    cur_max = 1
                    cur_min = 1
                    continue
    
                tmp = max(cur_max * x, cur_min * x, x)
                cur_min = min(cur_max * x, cur_min * x, x)
                cur_max = tmp
    
                result = max(result, cur_max, cur_min)
    
            return result
    """.trimIndent())

  @TestFor(issues = ["PY-87976"])
  @Test
  fun `Type variable should be passed to the return type unsolved`() = test("""
    from typing import Callable, assert_type
    def f[T]() -> Callable[[T], T]: ...
    fn = f()
    assert_type(fn(1), int)
    """.trimIndent())

}
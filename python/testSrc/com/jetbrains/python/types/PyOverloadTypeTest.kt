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
 * Type and type-checker tests for [overloads][https://docs.python.org/3/library/typing.html#typing.overload]:
 * overload resolution and matching, overloaded return types, and `Overload[...]` type assignability.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyOverloadTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class OverloadResolutionAndImplementation {
    @Test
    @TestFor(issues = ["PY-22971"])
    fun `first overload and implementation in class`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON35),
      """
      from typing import overload
      class A:
          @overload
          def foo(self, value: int) -> int:
              pass
          @overload
          def foo(self, value: str) -> str:
              pass
          def foo(self, value):
              return None
      expr = A().foo(5)
      # └ TYPE int
      """,
    )

    @Test
    @TestFor(issues = ["PY-22971"])
    fun `top level first overload and implementation`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON35),
      """
      from typing import overload
      @overload
      def foo(value: int) -> int:
          pass
      @overload
      def foo(value: str) -> str:
          pass
      def foo(value):
          return None
      expr = foo(5)
      # └ TYPE int
      """,
    )

    @Test
    @TestFor(issues = ["PY-22971"])
    fun `first overload and implementation in imported class`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON35),
      """
      from b import A
      expr = A().foo(5)
      #└ TYPE int
      """,
      "b.py" to OVERLOAD_CLASS_MODULE,
    )

    @Test
    @TestFor(issues = ["PY-22971"])
    fun `first overload and implementation in imported module`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON35),
      """
      from b import foo
      expr = foo(5)
      #└ TYPE int
      """,
      "b.py" to OVERLOAD_TOPLEVEL_MODULE,
    )

    @Test
    @TestFor(issues = ["PY-22971"])
    fun `second overload and implementation in class`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON35),
      """
      from typing import overload
      class A:
          @overload
          def foo(self, value: int) -> int:
              pass
          @overload
          def foo(self, value: str) -> str:
              pass
          def foo(self, value):
              return None
      expr = A().foo("5")
      # └ TYPE str
      """,
    )

    @Test
    @TestFor(issues = ["PY-22971"])
    fun `top level second overload and implementation`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON35),
      """
      from typing import overload
      @overload
      def foo(value: int) -> int:
          pass
      @overload
      def foo(value: str) -> str:
          pass
      def foo(value):
          return None
      expr = foo("5")
      #└ TYPE str
      """,
    )

    @Test
    @TestFor(issues = ["PY-22971"])
    fun `second overload and implementation in imported class`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON35),
      """
      from b import A
      expr = A().foo("5")
      #└ TYPE str
      """,
      "b.py" to OVERLOAD_CLASS_MODULE,
    )

    @Test
    @TestFor(issues = ["PY-22971"])
    fun `second overload and implementation in imported module`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON35),
      """
      from b import foo
      expr = foo("5")
      # └ TYPE str
      """,
      "b.py" to OVERLOAD_TOPLEVEL_MODULE,
    )

    @Test
    @TestFor(issues = ["PY-22971"])
    fun `not matched overloads and implementation in class`() = test(
      """
      from typing import overload
      class A:
          @overload
          def foo(self, value: int) -> int:
              pass
          @overload
          def foo(self, value: str) -> str:
              pass
          def foo(self, value):
              return None
      expr = A().foo(object()) # WARNING No overload of 'foo' matches the arguments. Argument types: (object). Expected one of: (value: int), (value: str)
      #└ TYPE UnsafeUnion[int, str]
      """,
    )

    @Test
    @TestFor(issues = ["PY-22971"])
    fun `top level not matched overloads and implementation`() = test(
      """
      from typing import overload
      @overload
      def foo(value: int) -> int:
          pass
      @overload
      def foo(value: str) -> str:
          pass
      def foo(value):
          return None
      expr = foo(object()) # WARNING No overload of 'foo' matches the arguments. Argument types: (object). Expected one of: (value: int), (value: str)
      #└ TYPE UnsafeUnion[int, str]
      """,
    )

    @Test
    @TestFor(issues = ["PY-22971"])
    fun `not matched overloads and implementation in imported class`() = test(
      """
      from b import A
      expr = A().foo(object()) # WARNING No overload of 'foo' matches the arguments. Argument types: (object). Expected one of: (value: int), (value: str)
      #└ TYPE UnsafeUnion[int, str]
      """,
      "b.py" to OVERLOAD_CLASS_MODULE,
    )

    @Test
    @TestFor(issues = ["PY-22971"])
    fun `not matched overloads and implementation in imported module`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON35),
      """
      from b import foo
      expr = foo(object()) # WARNING No overload of 'foo' matches the arguments. Argument types: (object). Expected one of: (value: int), (value: str)
      # └ TYPE UnsafeUnion[int, str]
      """,
      "b.py" to OVERLOAD_TOPLEVEL_MODULE,
    )
  }

  @Nested
  inner class OverloadedReturnTypes {
    @Test
    @TestFor(issues = ["PY-40838"])
    fun `union of many types including literals from overloaded returns`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON27),
      """
      from typing import overload, Literal

      @overload
      def foo1() -> Literal["1"]:
      #         └ ERROR Return type annotations are unsupported in Python 2
          pass

      @overload
      def foo1() -> Literal[2]:
      #   │     └ ERROR Return type annotations are unsupported in Python 2
      #   ^^^^ WARNING This overload will never be matched as parameter types(s) of overload 1 are the same or broaderConflicting signature: '() -> Literal[2]'
          pass

      @overload
      def foo1() -> bool:
      #   │     └ ERROR Return type annotations are unsupported in Python 2
      #   ^^^^ WARNING This overload will never be matched as parameter types(s) of overload 1 are the same or broaderConflicting signature: '() -> bool'
      #   ^^^^ WARNING This overload will never be matched as parameter types(s) of overload 2 are the same or broaderConflicting signature: '() -> bool'
          pass

      @overload
      def foo1() -> None:
      #   │     └ ERROR Return type annotations are unsupported in Python 2
      #   ^^^^ WARNING This overload will never be matched as parameter types(s) of overload 1 are the same or broaderConflicting signature: '() -> None'
      #   ^^^^ WARNING This overload will never be matched as parameter types(s) of overload 2 are the same or broaderConflicting signature: '() -> None'
      #   ^^^^ WARNING This overload will never be matched as parameter types(s) of overload 3 are the same or broaderConflicting signature: '() -> None'
          pass

      def foo1()
      #         └ ERROR ':' expected
          pass

      expr = foo1()
      #└ TYPE Literal["1"]
      """,
    )

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `overloads with typing literal - literal argument`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON36),
      """
      from typing_extensions import Literal
      from typing import overload

      @overload
      def foo(p1: Literal["a"]) -> str: ...
      #   ^^^ WARNING This overload overlaps overload 3 with incompatible return typeConflicting signature: '(p1: Literal["a"]) -> str'

      @overload
      def foo(p1: Literal["b"]) -> bytes: ...
      #   ^^^ WARNING This overload overlaps overload 3 with incompatible return typeConflicting signature: '(p1: Literal["b"]) -> bytes'

      @overload
      def foo(p1: str) -> int: ...

      def foo(p1):
          pass

      a: Literal["a"]
      expr = foo(a)
      #└ TYPE str
      """,
    )

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `overloads with typing literal - widened argument`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON36),
      """
      from typing_extensions import Literal
      from typing import overload

      @overload
      def foo(p1: Literal["a"]) -> str: ...
      #   ^^^ WARNING This overload overlaps overload 3 with incompatible return typeConflicting signature: '(p1: Literal["a"]) -> str'

      @overload
      def foo(p1: Literal["b"]) -> bytes: ...
      #   ^^^ WARNING This overload overlaps overload 3 with incompatible return typeConflicting signature: '(p1: Literal["b"]) -> bytes'

      @overload
      def foo(p1: str) -> int: ...

      def foo(p1):
          pass

      a: str = "a"
      expr = foo(a)
      #└ TYPE int
      """,
    )

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `overloads with typing literal - literal expression argument`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON36),
      """
      from typing_extensions import Literal
      from typing import overload

      @overload
      def foo(p1: Literal["a"]) -> str: ...
      #   ^^^ WARNING This overload overlaps overload 3 with incompatible return typeConflicting signature: '(p1: Literal["a"]) -> str'

      @overload
      def foo(p1: Literal["b"]) -> bytes: ...
      #   ^^^ WARNING This overload overlaps overload 3 with incompatible return typeConflicting signature: '(p1: Literal["b"]) -> bytes'

      @overload
      def foo(p1: str) -> int: ...

      def foo(p1):
          pass

      expr = foo("a")
      #└ TYPE str
      """,
    )

    @Test
    fun `annotated cls return overloaded class method`() = test(
      """
      from mytime import mytime
      expr = mytime.now()
      #└ TYPE mytime
      """,
      "mytime.py" to """
        from typing import Type, TypeVar

        T = TypeVar("T")

        class mytime:
            if sys.version_info >= (3, 8):
                @classmethod
                def now(cls: Type[T], tz: Optional[int] = ...) -> T: ...
            else:
                @overload
                @classmethod
                def now(cls: Type[T], tz: int = ...) -> T: ...
        """,
    )
  }

  @Nested
  inner class OverloadResolutionOnDundersAndConstructors {
    @Test
    fun `parameter type inference in overloaded methods`() = test("""
      from typing import overload

      class Base:
          @overload
          def test(self, param: int) -> int: pass
      #       ^^^^ WARNING A series of @overload-decorated methods should always be followed by an implementation that is not @overload-ed

          @overload
          def test(self, param: str) -> str: pass

      class Subclass(Base):
          def test(self, param):
              expr = param
      #       └ TYPE Unknown
      """)

    @Test
    fun `slice expression uses correct getitem overload`() = test("""
      from typing import overload

      class A[T]:
          @overload
          def __getitem__(self, s: str) -> str: ...

          @overload
          def __getitem__(self, s: slice) -> T: ...

          def __getitem__(self, s: str | slice) -> str | T: ...

      expr = A[int]()[0:2]
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic class overloaded methods - first method`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import overload

      Shape = TypeVarTuple("Shape")
      Axis1 = TypeVar("Axis1")
      Axis2 = TypeVar("Axis2")
      Axis3 = TypeVar("Axis3")


      class Array(Generic[*Shape]):
         @overload
         def transpose(self: Array[Axis1, Axis2]) -> Array[Axis2, Axis1]: ...
      #      ^^^^^^^^^ WARNING Signature of this @overload-decorated method is not compatible with the implementation

         @overload
         def transpose(self: Array[Axis1, Axis2, Axis3]) -> Array[Axis3, Axis2, Axis1]: ...
      #      ^^^^^^^^^ WARNING Signature of this @overload-decorated method is not compatible with the implementation

         def transpose(self): ...


      a: Array[int, str] = Array()

      expr = a.transpose()
      #└ TYPE Array[str, int]
      """)

    @Test
    @TestFor(issues = ["PY-53105"])
    fun `variadic generic class overloaded methods - second method`() = test("""
      from __future__ import annotations

      from typing import TypeVarTuple
      from typing import TypeVar
      from typing import Generic
      from typing import overload

      Axis1 = TypeVar("Axis1")
      Axis2 = TypeVar("Axis2")
      Axis3 = TypeVar("Axis3")


      class Array[*Shape]:
         @overload
         def transpose(self: Array[Axis1, Axis2]) -> Array[Axis2, Axis1]: ...
      #      ^^^^^^^^^ WARNING Signature of this @overload-decorated method is not compatible with the implementation

         @overload
         def transpose(self: Array[Axis1, Axis2, Axis3]) -> Array[Axis3, Axis2, Axis1]: ...
      #      ^^^^^^^^^ WARNING Signature of this @overload-decorated method is not compatible with the implementation

         def transpose(self): ...


      a: Array[int, str, list[int]] = Array()

      expr = a.transpose()
      #└ TYPE Array[list[int], str, int]
      """)

    @Test
    fun `generic self specialization in overloaded constructor`() = test("""
      from typing import Generic, TypeVar, overload

      T1 = TypeVar('T1')
      T2 = TypeVar('T2')

      class Pair(Generic[T1, T2]):
          @overload
          def __init__(self: 'Pair[str, str]', value: str):
      #       ^^^^^^^^ WARNING A series of @overload-decorated methods should always be followed by an implementation that is not @overload-ed
              pass

          @overload
          def __init__(self: 'Pair[int, int]', value: int):
              pass

      expr = Pair(42)
      #└ TYPE Pair[int, int]
      """)

    @Test
    @TestFor(issues = ["PY-64481"])
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
  }

  @Nested
  inner class OverloadTypeRenderingAndAssignability {
    @Test
    fun `overload type of implementation reference`() = test("""
      from typing import overload

      @overload
      def foo(x: int) -> str: ...

      @overload
      def foo(x: str) -> int: ...

      def foo(x): ...

      expr = foo
      #└ TYPE Overload[(x: int) -> str, (x: str) -> int]
      """)

    @Test
    fun `overload type from stub`() = test(
      """
      from stub import foo

      expr = foo
      #└ TYPE Overload[(x: int) -> str, (x: str) -> int]
      """,
      "stub.pyi" to """
        from typing import overload

        @overload
        def foo(x: int) -> str: ...

        @overload
        def foo(x: str) -> int: ...
        """,
    )

    @Test
    @TestFor(issues = ["PY-52839"])
    fun `overload assignability to callable`() = test("""
      from typing import Callable, overload

      @overload
      def foo(x: int) -> int: ...

      @overload
      def foo(x: str) -> str: ...

      def foo(x: object) -> object: ...

      _: Callable[[int], int] = foo  # ok
      _: Callable[[str], str] = foo  # ok
      _: Callable[[int], str] = foo # WARNING Expected type '(int) -> str', got 'Overload[(x: int) -> int, (x: str) -> str]' instead
      """)

    @Test
    @TestFor(issues = ["PY-52839"])
    fun `assignability to overload`() = test("""
      from typing import Callable, overload

      @overload
      def foo(x: int) -> int: ...

      @overload
      def foo(x: str) -> str: ...

      def foo(x: object) -> object: ...

      @overload
      def foo2(x: str) -> str: ...

      @overload
      def foo2(x: int) -> int: ...

      def foo2(x: object) -> object: ...

      @overload
      def bar(x: int) -> int: ...

      @overload
      def bar(x: str) -> int: ...

      def bar(x: object) -> object: ...

      def baz(x: int) -> int: ...

      l = [foo]
      l.append(foo)  # ok
      l.append(foo2)  # ok
      l.append(bar) # WARNING Expected type 'Overload[(x: int) -> int, (x: str) -> str]', got 'Overload[(x: int) -> int, (x: str) -> int]' instead
      l.append(baz) # WARNING Expected type 'Overload[(x: int) -> int, (x: str) -> str]', got '(x: int) -> int' instead
      """)

    @Test
    @TestFor(issues = ["PY-52839"])
    fun `overload with callable protocol`() = test("""
      from typing import overload, Protocol

      class ConverterProtocol(Protocol):
          @overload
          def __call__(self, x: int) -> str: ...

          @overload
          def __call__(self, x: str) -> int: ...

      class CompatibleCallable:
          @overload
          def __call__(self, x: str) -> int: ...

          @overload
          def __call__(self, x: int) -> str: ...

          def __call__(self, x: object) -> object: ...

      class IncompatibleCallable:
          @overload
          def __call__(self, x: int) -> int: ...

          @overload
          def __call__(self, x: str) -> str: ...

          def __call__(self, x: object) -> object: ...


      @overload
      def converter_func(x: str) -> int: ...

      @overload
      def converter_func(x: int) -> str: ...

      def converter_func(x: object) -> object: ...

      @overload
      def bad_converter_func(x: str) -> str: ...

      @overload
      def bad_converter_func(x: int) -> int: ...

      def bad_converter_func(x: object) -> object: ...

      c1: ConverterProtocol = CompatibleCallable()  # ok
      c2: ConverterProtocol = IncompatibleCallable() # WARNING Expected type 'ConverterProtocol', got 'IncompatibleCallable' instead
      c3: ConverterProtocol = converter_func  # ok
      c3: ConverterProtocol = bad_converter_func
      #│                      ^^^^^^^^^^^^^^^^^^ WARNING Expected type 'ConverterProtocol', got 'Overload[(x: str) -> str, (x: int) -> int]' instead
      #\ WARNING Redeclared 'c3' defined above without usage

      def t(c: ConverterProtocol):
          l3 = [converter_func]
          l3.append(c)

          l4 = [bad_converter_func]
          l4.append(c) # WARNING Expected type 'Overload[(x: str) -> str, (x: int) -> int]', got 'ConverterProtocol' instead
      """)

    @Test
    @TestFor(issues = ["PY-52839"])
    fun `overload subset matching`() = test("""
      from typing import overload, Callable

      @overload
      def many_overloads(x: int) -> int: ...

      @overload
      def many_overloads(x: str) -> str: ...

      @overload
      def many_overloads(x: float) -> float: ...

      def many_overloads(x: object) -> object: ...


      @overload
      def few_overloads(x: str) -> str: ...

      @overload
      def few_overloads(x: int) -> int: ...

      def few_overloads(x: object) -> object: ...

      # Assigning to list infers the overload type
      l1 = [few_overloads]
      l1.append(many_overloads)  # ok

      l2 = [many_overloads]
      l2.append(few_overloads) # WARNING Expected type 'Overload[(x: int) -> int, (x: str) -> str, (x: float | int) -> float | int]', got 'Overload[(x: str) -> str, (x: int) -> int]' instead
      """)

    @Test
    fun `callable subtyping with overloads`() = test("""
      from typing import Protocol, overload

      class Overloaded(Protocol):
          @overload
          def __call__(self, x: int) -> int: ...
          @overload
          def __call__(self, x: str) -> str: ...

      class IntArg(Protocol):
          def __call__(self, x: int) -> int: ...

      class StrArg(Protocol):
          def __call__(self, x: str) -> str: ...

      class FloatArg(Protocol):
          def __call__(self, x: float) -> float: ...

      def func(overloaded: Overloaded):
          f1: IntArg = overloaded  # OK
          f2: StrArg = overloaded  # OK
          f3: FloatArg = overloaded # WARNING Expected type 'FloatArg', got 'Overloaded' instead
      """)

    @Test
    fun `callable subtyping with overloads - widened parameters`() = test("""
      from typing import Protocol, overload

      class Overloaded(Protocol):
          @overload
          def __call__(self, x: int, y: str) -> float: ...
          @overload
          def __call__(self, x: str) -> complex: ...

      class StrArg(Protocol):
          def __call__(self, x: str) -> complex: ...

      class IntStrArg(Protocol):
          def __call__(self, x: int | str, y: str = "") -> int: ...

      def func(int_str_arg: IntStrArg, str_arg: StrArg):
          f1: Overloaded = int_str_arg  # OK
          f2: Overloaded = str_arg # WARNING Expected type 'Overloaded', got 'StrArg' instead
      """)

    @Test
    @TestFor(issues = ["PY-87801"])
    fun `callable protocol with overloads - plain function assignment`() = test("""
      from typing import Protocol, overload, Any

      class Proto(Protocol):
          @overload
          def __call__(self, x: int) -> int:
              ...

          @overload
          def __call__(self, x: str) -> str:
              ...

          def __call__(self, x: Any) -> Any:
              ...

      def f(x: int) -> Any:
          return x

      cb: Proto = f # WARNING Expected type 'Proto', got '(x: int) -> Any' instead
      """)

    @Test
    @TestFor(issues = ["PY-87801"])
    fun `callable protocol with overloads - overloaded function assignment`() = test("""
      from typing import Protocol, overload, Any

      class Proto(Protocol):
          @overload
          def __call__(self, x: int) -> int:
              ...

          @overload
          def __call__(self, x: str) -> str:
              ...

          def __call__(self, x: Any) -> Any:
              ...

      @overload
      def f(x: str) -> str: ...

      @overload
      def f(x: int) -> int: ...

      def f(x: Any) -> Any:
          return x

      cb: Proto = f
      """)

    @Test
    @TestFor(issues = ["PY-87801"])
    fun `callable protocol with overloads - non-matching overloaded function assignment`() = test("""
      from typing import Protocol, overload, Any

      class Proto(Protocol):
          @overload
          def __call__(self, x: int) -> int:
              ...

          @overload
          def __call__(self, x: str) -> str:
              ...

          def __call__(self, x: Any) -> Any:
              ...

      class A:
          pass

      @overload
      def f(x: str) -> str: ...

      @overload
      def f(x: A) -> A: ...

      def f(x: Any) -> Any:
          return x

      cb: Proto = f # WARNING Expected type 'Proto', got 'Overload[(x: str) -> str, (x: A) -> A]' instead
      """)

    @Test
    @TestFor(issues = ["PY-76399"])
    fun `assigned value matches with dunder set with overloads`() = test("""
      from typing import overload

      class MyDescriptor:

          @overload
          def __set__(self, obj: "Test", value: str) -> None:
              ...
          @overload
          def __set__(self, obj: "Prod", value: "LocalizedString") -> None:
              ...
          def __set__(self, obj, value) -> None:
              ...

      class Test:
          member: MyDescriptor

      class Prod:
          member: MyDescriptor

      class LocalizedString:
          def __init__(self, value: str):
              ...

      t = Test()
      t.member = "abc"
      t.member = 42 # WARNING Expected type 'str' (from '__set__'), got 'Literal[42]' instead
      p = Prod()
      p.member = "abc" # WARNING Expected type 'LocalizedString' (from '__set__'), got 'Literal["abc"]' instead
      p.member = 42 # WARNING Expected type 'LocalizedString' (from '__set__'), got 'Literal[42]' instead
      """)

    @Test
    @TestFor(issues = ["PY-88578"])
    fun `ellipsis in overload`() = test("""
      from typing import overload

      @overload
      def f(a: str): ...

      @overload
      def f(a: None = (...)): ...

      def f(a:str | None = ...):
      #                    ^^^ WARNING Expected type 'str | None', got 'EllipsisType' instead
          print(a)
      """)
  }

  companion object {
    private val OVERLOAD_CLASS_MODULE = """
      from typing import overload


      class A:
          @overload
          def foo(self, value: int) -> int:
              pass

          @overload
          def foo(self, value: str) -> str:
              pass

          def foo(self, value):
              return None
      """

    private val OVERLOAD_TOPLEVEL_MODULE = """
      from typing import overload


      @overload
      def foo(value: int) -> int:
          pass


      @overload
      def foo(value: str) -> str:
          pass


      def foo(value):
          return None
      """
  }

  @Test
  @TestFor(issues = ["PY-27788"])
  fun `overloaded function assigned to target in stub`() = test(
    """
    from stub import good, bad

    good('foo')
    good(5)

    bad('foo')
    bad(15)
    """,
    "stub.pyi" to """
      from typing import overload

      @overload
      def good(default: int) -> int: ...
      @overload
      def good(default: str) -> str: ...

      bad = good
      """,
  )

  @Test
  @TestFor(issues = ["PY-39762"])
  fun `overloads and pure stub function in the same pyi scope`() = test(
    """
    from module import foo, bar

    foo(5)
    foo("str", 5)
    foo([5]) # WARNING Expected type 'int', got 'list[Literal[5]]' instead

    bar("str")
    bar(5)
    bar([5]) # WARNING No overload of 'bar' matches the arguments. Argument types: (list[Literal[5]]). Expected one of: (p: str), (p: int)
    """,
    "module.pyi" to """
      import sys
      from typing import overload

      if sys.version_info < (3, ):
          def foo(p: str) -> str: pass
      else:
          @overload
          def foo(p: int) -> int: pass
          @overload
          def foo(p: str, i: int) -> str: pass

      @overload
      def bar(p: str) -> str: pass

      @overload
      def bar(p: int) -> int: pass
      """,
  )

  @Test
  fun `class overloaded method bound and imported as global function`() = test(
    """
    from a import f

    i: str = f(1)
    """,
    "a.py" to """
      from typing import overload


      class A:
          @overload
          def f(self) -> float:
              pass

          @overload
          def f(self, *args: int) -> str:
              pass


      a: A
      f = a.f
      """,
  )

  @Test
  @TestFor(issues=["PY-90419"])
  fun `Any in overload becomes an unsafe union`() = test("""
    from typing import overload, Any


    @overload
    def f(a: int) -> int: ...
    @overload
    def f(a: str) -> str: ...
    def f(a): ...

    def main(a: Any):
        x = f(a)
        x.as_integer_ratio()
    #   └ TYPE UnsafeUnion[int, str]
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-84657"])
  fun `class overloaded function assigned to global function`() = test(
    """
    from mod import a, f

    result1 = a.f(1)
    # └ TYPE str
    result2 = f(1)
    # └ TYPE str
    """,
    "mod.py" to """
    from typing import overload
    
    
    class A:
       @overload
       def f(self) -> float:
           pass
    
       @overload
       def f(self, *args: int) -> str:
           pass
    
    
    a: A
    f = a.f
    """)

  @Test
  @TestFor(issues = ["PY-84004"])
  fun `module overloaded function assigned to global function`() = test(
    """
    from mod import g

    expr1 = g(1)
    # └ TYPE bytes
    expr2 = g("s")
    # └ TYPE str
    """,
    "mod.py" to """
    import lib

    g = lib.func
    """,
    "lib.py" to """
    from typing import overload


    @overload
    def func(x: int) -> bytes: ...
    @overload
    def func(x: str) -> str: ...
    def func(x): ...
    """)

  @Test
  @TestFor(issues=["PY-90419"])
  fun `combining CFG-induced unions and unsafe unions for ambiguous overloads`() = test("""
    from typing import overload, Any

    class A:
        @overload
        def f(self, a: int) -> int: ...
        @overload
        def f(self, a: str) -> str: ...
        def f(self, a): ...
        
    class B:
        @overload
        def f(self, a: int) -> bool: ...
        @overload
        def f(self, a: str) -> str: ...
        def f(self, a): ...

    def main(cond: bool, a: Any):
        if cond:
            obj = A()
        else:
            obj = B()
        x = obj.f(a)
        x.as_integer_ratio()
    #   └ TYPE UnsafeUnion[int, str] | UnsafeUnion[bool, str]
    """.trimIndent())

}

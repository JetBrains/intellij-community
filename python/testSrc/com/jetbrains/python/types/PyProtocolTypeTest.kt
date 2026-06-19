// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.intellij.openapi.util.StackOverflowPreventedException
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.fixtures.PyTestCase.fixme
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for [typing.Protocol][https://docs.python.org/3/library/typing.html#typing.Protocol]:
 * structural conformance, protocol assignability/subtyping, generic protocols and protocol members.
 */
class PyProtocolTypeTest : PyCodeInsightTestCase() {

  override val defaultTestOptions = TestOptions(enablePyAnyType = false)

  @Nested
  inner class GenericProtocolStructuralConformanceAndUnification {

    @Test
    @TestFor(issues = ["PY-26628"])
    fun `generic protocol parameterized via subclass`() = test("""
      from typing import Protocol
      
      class MyProto1[T](Protocol):
          def func(self) -> T:
              pass
      class MyClass1(MyProto1[int]):
          pass
      expr = MyClass1().func()
      # └ TYPE int
      """
    )

    @Test
    @TestFor(issues = ["PY-85123"])
    fun `generic return type matched against protocol`() = test("""
      from typing_extensions import reveal_type, Protocol, TypeVar

      _T_co = TypeVar("_T_co", covariant=True)

      class P(Protocol[_T_co]):
          def f(self) -> _T_co: ...

      class C:
          def f(self) -> int:
              return 1

      def a[_T](p1: P[_T]) -> _T:
          return p1.f()

      expr = a(C())
      # └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-88326"])
    fun `generic protocol unification from classmethod self annotation`() = test("""
      from typing import Protocol

      class ProtoA[T](Protocol):
          @classmethod
          def method1(cls, value: T) -> None:
              ...

      class ProtoB[T](Protocol):
          def method2(self) -> T:
              ...

      class ImplB:
          def method2(self) -> int:
              return 0

          @classmethod
          def method1[T](cls: type[ProtoB[T]], value: list[T]) -> None:
              pass

      def func1[T](x: ProtoA[T]) -> T:
          raise NotImplementedError

      expr = func1(ImplB())
      #└ TYPE list[int]
      """)

    @Test
    fun `generic protocol unification with the same type variable`() = test("""
      from typing import Protocol
      from typing import TypeVar

      T = TypeVar('T', covariant=True)

      class SupportsIter(Protocol[T]):
          def __iter__(self) -> T:
              pass

      def my_iter(x: SupportsIter[T]) -> T:
          pass

      class MyList:
          def __iter__(self) -> list[int]:
              pass

      expr = my_iter(MyList())
      #└ TYPE list[int]
      """)

    @Test
    fun `generic protocol unification with a separate type variable`() = test("""
      from typing import Protocol
      from typing import TypeVar

      T = TypeVar('T', covariant=True)
      T2 = TypeVar('T2')

      class SupportsIter(Protocol[T]):
          def __iter__(self) -> T:
              pass

      def my_iter(x: SupportsIter[T2]) -> T2:
          pass

      class MyList:
          def __iter__(self) -> list[int]:
              pass

      expr = my_iter(MyList())
      # └ TYPE list[int]
      """)

    @Test
    fun `generic protocol unification with generic implementation`() = test("""
      from typing import Generic, Protocol

      class Fooable[T1](Protocol):
          def foo(self) -> T1:
              ...

      class MyClass[T2]:
          def foo(self) -> T2:
              ...

      def f[T1](x: Fooable[T1]) -> T1:
          ...

      obj: MyClass[int]
      expr = f(obj)
      #└ TYPE int
      """)

    @Test
    fun `generic protocol unification with nongeneric implementation with generic superclass`() = test("""
      from typing import Generic, Protocol

      class Fooable[T1](Protocol):
          def foo(self) -> T1:
              ...

      class Super[T2]:
          def foo(self) -> T2:
              ...

      class MyClass(Super[int]):
          pass

      def f[T1](x: Fooable[T1]) -> T1:
          ...

      obj: MyClass
      expr = f(obj)
      #└ TYPE int
      """)

    @Test
    fun `generic protocol unification with generic implementation with generic superclass`() = test("""
      from typing import Generic, Protocol

      class Fooable[T1](Protocol):
          def foo(self) -> T1:
              ...

      class Super[T2]:
          def foo(self) -> T2:
              ...

      class MyClass[T2](Super[T2]):
          pass

      def f[T1](x: Fooable[T1]) -> T1:
          ...

      obj: MyClass[int]
      expr = f(obj)
      #└ TYPE int
      """)

    @Test
    fun `generic protocol unification with generic implementation with generic superclass and extra parameter`() = test("""
      from typing import Generic, Protocol

      class Fooable[T1](Protocol):
          def foo(self) -> T1:
              ...

      class Super[T2]:
          def foo(self) -> T2:
              ...

      class MyClass[T1](Super[int]):
          pass

      def f[T1](x: Fooable[T1]) -> T1:
          ...

      obj: MyClass[str]
      expr = f(obj)
      # └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic protocol unification with the same type variable with PEP695 syntax`() = test("""
      from typing import Protocol

      class SupportsIter[T](Protocol):
          def __iter__(self) -> T:
              pass


      def my_iter[T](x: SupportsIter[T]) -> T:
          pass


      class MyList:
          def __iter__(self) -> list[int]:
              pass


      expr = my_iter(MyList())
      #└ TYPE list[int]
      """)

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic protocol unification with a separate type variable with PEP695 syntax`() = test("""
      from typing import Protocol

      class SupportsIter[T](Protocol):
          def __iter__(self) -> T:
              pass

      def my_iter[T2](x: SupportsIter[T2]) -> T2:
          pass

      class MyList:
          def __iter__(self) -> list[int]:
              pass

      expr = my_iter(MyList())
      #└ TYPE list[int]
      """)

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic protocol unification with generic implementation with generic superclass with PEP695 syntax`() = test("""
      from typing import Protocol


      class Fooable[T1](Protocol):
          def foo(self) -> T1:
              ...

      class Super[T2]:
          def foo(self) -> T2:
              ...

      class MyClass[T2](Super[T2]):
          pass

      def f[T1](x: Fooable[T1]) -> T1:
          ...

      obj: MyClass[int]
      expr = f(obj)
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-76902"])
    fun `class inherits protocol to order type parameters`() = test("""
      from typing import Protocol

      class Box[T1](Protocol):
          def get(self) -> T1:
              pass

      class Pair[T1, T2](Box[T2], Protocol):
          pass

      xs: Pair[int, str] = ...
      #                    ^^^ WARNING Expected type 'Pair[int, str]', got 'EllipsisType' instead
      expr = xs.get()
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-86463"])
    fun `matching with inherited generic protocol`() = test("""
      from typing import Protocol

      class P[T](Protocol):
          def method(self, x: T) -> T:
              pass

      class P2[T](P[T], Protocol):
          pass

      class Impl:
          def method(self, x: int) -> int:
              return 42

      def expects_generic[T](x: P2[T]) -> T:
          return x.method()
      #                   └ WARNING Parameter 'x' unfilled

      expr = expects_generic(Impl())
      #└ TYPE int
      """)
  }

  @Nested
  inner class ProtocolAssignabilitySubtypingInspectionWarnings {

    @TestFor(issues = ["PY-53104"])
    @Test
    fun `protocol method returning Self matches concrete returning own class`() = test("""
      from __future__ import annotations
      from typing import Self, Protocol


      class MyProtocol(Protocol):
          def foo(self, bar: float) -> Self: ...


      class MyClass:
          def foo(self, bar: float) -> MyClass:
              pass


      def accepts_protocol(obj: MyProtocol) -> None:
          print(obj)


      obj = MyClass()
      accepts_protocol(obj)
      """)

    @TestFor(issues = ["PY-53104"])
    @Test
    fun `protocol method returning Self matches concrete returning subclass`() = test("""
      from __future__ import annotations
      from typing import Self, Protocol


      class MyProtocol(Protocol):
          def foo(self, bar: float) -> Self: ...


      class MyClass:
          def foo(self, bar: float) -> MySubClass:
              pass


      class MySubClass(MyClass):
          pass


      def accepts_protocol(obj: MyProtocol) -> None:
          print(obj)


      obj = MyClass()
      accepts_protocol(obj)
      """)

    @TestFor(issues = ["PY-53104"])
    @Test
    fun `protocol method returning Self rejects concrete returning unrelated class`() = test("""
      from __future__ import annotations
      from typing import Self, Protocol


      class MyProtocol(Protocol):
          def foo(self, bar: float) -> Self: ...


      class MyClass:
          def foo(self, bar: float) -> int:
              pass


      def accepts_protocol(obj: MyProtocol) -> None:
          print(obj)


      obj = MyClass()
      accepts_protocol(obj) # WARNING Expected type 'MyProtocol', got 'MyClass' instead
      """)

    @TestFor(issues = ["PY-53104"])
    @Test
    fun `protocol method returning Self rejects concrete returning non subclass`() = test("""
      from __future__ import annotations
      from typing import Self, Protocol


      class MyProtocol(Protocol):
          def foo(self, bar: float) -> Self: ...


      class MyClass:
          def foo(self, bar: float) -> MyClassNotSubclass:
              pass


      class MyClassNotSubclass:
          def foo(self, bar: float) -> int:
              pass


      def accepts_protocol(obj: MyProtocol) -> None:
          print(obj)


      obj = MyClass()
      accepts_protocol(obj) # WARNING Expected type 'MyProtocol', got 'MyClass' instead
      """)

    @TestFor(issues = ["PY-53104"])
    @Test
    fun `protocol method returning Self matches concrete returning Self`() = test("""
      from __future__ import annotations
      from typing import Self, Protocol


      class MyProtocol(Protocol):
          def foo(self, bar: float) -> Self: ...


      class MyClass:
          def foo(self, bar: float) -> Self:
              pass


      def accepts_protocol(obj: MyProtocol) -> None:
          print(obj)


      obj = MyClass()
      accepts_protocol(obj)
      """)

    @Test
    fun `nongeneric protocol does not match generic class`() = test("""
      from typing import Generic, Protocol, TypeVar

      T = TypeVar('T')

      class IntGetter(Protocol):
          def get(self) -> int:
              pass

      class Box(Generic[T]):
          def get(self) -> T:
              pass

      def f(x: IntGetter):
          pass

      box: Box[str]
      f(box) # WARNING Expected type 'IntGetter', got 'Box[str]' instead
      """)

    @Test
    fun `generic protocol does not match generic class with wrong argument`() = test("""
      from typing import Generic, Protocol

      class Getter[T](Protocol):
          def get(self) -> T:
              pass

      class Box[T]:
          def get(self) -> T:
              pass

      def f(x: Getter[int]):
          pass

      box: Box[str]
      f(box) # WARNING Expected type 'Getter[int]', got 'Box[str]' instead
      """)

    @Test
    @TestFor(issues = ["PY-85123"])
    fun `overloaded method in concrete class matches generic protocol`() = test("""
      from typing import TypeVar, overload, Protocol

      T = TypeVar("T", contravariant=True)

      class SupportsWrite(Protocol[T]):
          def write(self, s: T): ...

      class B:
          @overload
          def write(self, s: int): ...
      #       ^^^^^ WARNING A series of @overload-decorated methods should always be followed by an implementation that is not @overload-ed

          @overload
          def write(self, s: str): ...


      a: SupportsWrite[str] = B()
      """)

    @Test
    @TestFor(issues = ["PY-85123"])
    fun `protocol partial specialization with fixed return and generic parameter`() = test("""
      from typing import Protocol, TypeVar, overload

      T = TypeVar("T", contravariant=True)
      S = TypeVar("S", covariant=True)

      class P(Protocol[T, S]):
          def write(self, x: T) -> S: ...

      class B:
          @overload
          def write(self, x: int) -> str: ...
      #       ^^^^^ WARNING A series of @overload-decorated methods should always be followed by an implementation that is not @overload-ed
          @overload
          def write(self, x: str) -> str: ...


      def accepts_p(arg: P[T, str]) -> None: ...
      accepts_p(B())
      """)

    @Test
    @TestFor(issues = ["PY-85123"])
    fun `protocol partial specialization with union of concrete and generic`() = test("""
      from typing import Protocol, TypeVar, overload

      T = TypeVar("T", contravariant=True)

      class SupportsWrite(Protocol[T]):
          def write(self, s: T): ...

      class B:
          @overload
          def write(self, s: int): ...
      #       ^^^^^ WARNING A series of @overload-decorated methods should always be followed by an implementation that is not @overload-ed
          @overload
          def write(self, s: str): ...


      def accepts_union(x: SupportsWrite[str] | SupportsWrite[T]) -> None: ...
      accepts_union(B())
      """)

    @Test
    @TestFor(issues = ["PY-86463"])
    fun `inherited generic protocol rejects nonmatching implementation`() = test("""
      from typing import Protocol, overload

      class P[T](Protocol):
          def method(self, x: T) -> T:
              pass

      class P2[T](P[T], Protocol):
          pass

      class Impl:
          def method(self, x: int) -> int:
              ...

      def expects_P2_str(x: P2[str]):
          pass

      expr = expects_P2_str(Impl()) # WARNING Expected type 'P2[str]', got 'Impl' instead
      """)

    @Test
    @TestFor(issues = ["PY-86249"])
    fun `protocol with abstract method matches frozen dataclass`() = test("""
      import abc
      import dataclasses
      from typing import Protocol


      class Proto(Protocol):
          @abc.abstractmethod
          def to_kwargs(self) -> dict:
              pass


      @dataclasses.dataclass(frozen=True)
      class Impl:
          name: str

          def to_kwargs(self) -> dict:
              return {"name": self.name}


      def do(arg: Proto) -> None: ...


      do(Impl(name="vrf1"))
      """)

    @Test
    fun `overloaded method in concrete class matches protocol`() = test("""
      from typing import Protocol, ClassVar, overload

      class Template(Protocol):
          def f(self, x: int) -> int: ...


      class Concrete:
          @overload
          def f(self, x: str) -> int: ...

          @overload
          def f(self, x: int) -> int: ...

          def f(self, x) -> int:
              return 1

      var: Template = Concrete()
      """)
  }

  @Nested
  inner class ProtocolMembersPropertiesClassVarAttributes {

    @Test
    @TestFor(issues = ["PY-76822"])
    fun `protocol with attribute assigned in method matches concrete`() = test("""
      from typing import Protocol

      class Template(Protocol):
          name: str
          value: int = 0

          def method(self) -> None:
              self.name = "name"
              self.temp: list[int] = []


      class Concrete:
          def __init__(self, name: str, value: int) -> None:
              self.name = name
              self.value = value

          def method(self) -> None:
              return


      var: Template = Concrete("value", 42)
      """)

    @Test
    @TestFor(issues = ["PY-76822"])
    fun `protocol property matches concrete attribute`() = test("""
      from typing import Protocol

      class Template(Protocol):
          @property
          def val1(self) -> int:
              ...


      class Concrete:
          val1: int = 0

      var: Template = Concrete()
      """)

    @Test
    @TestFor(issues = ["PY-76822"])
    fun `protocol property matches concrete property`() = test("""
      from typing import Protocol

      class Template(Protocol):
          @property
          def val1(self) -> int:
              ...


      class Concrete:
          @property
          def val1(self) -> int:
              ...

      var: Template = Concrete()
      """)

    @Test
    @TestFor(issues = ["PY-76822"])
    fun `protocol property with setter rejects concrete with deleter`() = test("""
      from typing import Protocol

      class Template(Protocol):
          @property
          def val1(self) -> int:
              ...

          @val1.setter
          def val1(self, val: int) -> None:
              ...


      class Concrete:
          @property
          def val1(self) -> int:
              ...

          @val1.deleter
          def val1(self, val: int) -> None:
              ...

      var: Template = Concrete() # WARNING Expected type 'Template', got 'Concrete' instead
      """)

    @Test
    @TestFor(issues = ["PY-76822"])
    fun `protocol property with setter rejects frozen dataclass`() = test("""
      from typing import Protocol
      from dataclasses import dataclass

      class Template(Protocol):
          @property
          def val(self) -> int:
              ...

          @val.setter
          def val(self, val: int) -> None:
              ...


      @dataclass(frozen=True)
      class Concrete:
          val: int = 0

      var: Template = Concrete() # WARNING Expected type 'Template', got 'Concrete' instead
      """)

    @Test
    @TestFor(issues = ["PY-76822"])
    fun `protocol ClassVar matches concrete ClassVar`() = test("""
      from typing import Protocol, ClassVar

      class Template(Protocol):
          val: ClassVar[int] = 0


      class Concrete:
          val: ClassVar[int] = 0

      var: Template = Concrete()
      """)

    @Test
    @TestFor(issues = ["PY-76822"])
    fun `protocol ClassVar rejects concrete instance var`() = test("""
      from typing import Protocol, ClassVar

      class Template(Protocol):
          val: ClassVar[int] = 0


      class Concrete:
          val: int = 0

      var: Template = Concrete() # WARNING Expected type 'Template', got 'Concrete' instead
      """)

    @Test
    @TestFor(issues = ["PY-76822"])
    fun `protocol instance var rejects concrete ClassVar`() = test("""
      from typing import Protocol, ClassVar

      class Template(Protocol):
          val: int = 0


      class Concrete:
          val: ClassVar[int] = 0

      var: Template = Concrete() # WARNING Expected type 'Template', got 'Concrete' instead
      """)

    @Test
    @TestFor(issues = ["PY-76822"])
    fun `protocol property with deleter rejects frozen dataclass`() = test("""
      from typing import Protocol
      from dataclasses import dataclass

      class Template(Protocol):
          @property
          def val(self) -> int:
              ...

          @val.deleter
          def val(self, val: int) -> None:
              ...


      @dataclass(frozen=True)
      class Concrete:
          val: int = 0

      var: Template = Concrete() # WARNING Expected type 'Template', got 'Concrete' instead
      """)

    @Test
    @TestFor(issues = ["PY-76822"])
    fun `explicit Any in concrete type matches protocol`() = test("""
      from typing import Protocol, Any

      class Template(Protocol):
          val: int


      class Concrete:
          val: Any

      var: Template = Concrete()
      """)

    @Test
    @TestFor(issues = ["PY-76822"])
    fun `explicit Any in protocol matches concrete`() = test("""
      from typing import Protocol, Any

      class Template(Protocol):
          val: Any


      class Concrete:
          val: int

      var: Template = Concrete()
      """)

    @Test
    @TestFor(issues = ["PY-76822"])
    fun `explicit Any in both protocol and concrete type`() = test("""
      from typing import Protocol, Any

      class Template(Protocol):
          val: Any


      class Concrete:
          val: Any

      var: Template = Concrete()
      """)

    @Test
    fun `ellipsis default argument in protocol method is allowed`() = test("""
      from typing import Protocol

      class A(Protocol):
          def f(self, a: str = ...):
              pass
      """)

    @Test
    @TestFor(issues = ["PY-87801"])
    fun `callable protocol with additional attribute rejects plain function`() = test("""
      from typing import Protocol

      class Proto(Protocol):
          other_attribute: int

          def __call__(self, x: int) -> None:
              pass


      def f(x: int) -> None:
          pass


      v: Proto = f # WARNING Expected type 'Proto', got '(x: int) -> None' instead
      """)
  }

  @Nested
  inner class ImplicitProtocolMatchingIterableIteratorStructuralConformance {

    @Test
    @TestFor(issues = ["PY-24834"])
    fun `strict union implicit protocol matching`() = test("""
      from typing import Any


      class A:
          def __iter__(self):
              return self

          def __next__(self):
              return 42


      class B:
          def __iter__(self):
              return self

          def __next__(self):
              return 42


      class C:
          pass


      def all_union_members_match_no_any(iterable: A | B):
          for _ in iterable:
              pass


      def some_union_members_match_no_any(iterable: A | B | None):
          for _ in iterable: # WARNING Expected type 'collections.Iterable', got 'A | B | None' instead
              pass


      def all_union_members_dont_match_no_any(iterable: C | None):
          for _ in iterable: # WARNING Expected type 'collections.Iterable', got 'C | None' instead
              pass


      def all_union_members_match_with_any(iterable: A | B | Any):
          for _ in iterable:
              pass


      def some_union_members_match_with_any(iterable: A | B | None | Any):
          for _ in iterable: # WARNING Expected type 'collections.Iterable', got 'A | B | None | Any' instead
              pass


      def all_union_members_dont_match_with_any(iterable: C | None | Any):
          for _ in iterable: # WARNING Expected type 'collections.Iterable', got 'C | None | Any' instead
              pass
      """)

    @Test
    @TestFor(issues = ["PY-76922"])
    fun `intersection implicit protocol matching`() = test("""
      from typing import Any


      class A:
          def __iter__(self):
              return self

          def __next__(self):
              return 42


      class B:
          def __iter__(self):
              return self

          def __next__(self):
              return 42


      class C:
          pass


      def all_intersection_members_match_no_any(iterable: "A & B"):
      #                                                      └ WARNING Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances
          for _ in iterable:
              pass


      def some_intersection_members_match_no_any(iterable: "A & B & None"):
      #                                                       └ WARNING Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances
          for _ in iterable:
              pass


      def all_intersection_members_dont_match_no_any(iterable: "C & None"):
      #                                                           └ WARNING Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances
          for _ in iterable: # WARNING Expected type 'collections.Iterable', got 'C & None' instead
              pass


      def all_intersection_members_match_with_any(iterable: "A & B & Any"):
      #                                                        └ WARNING Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances
          for _ in iterable:
              pass


      def some_intersection_members_match_with_any(iterable: "A & B & None & Any"):
      #                                                         └ WARNING Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances
          for _ in iterable:
              pass


      def all_intersection_members_dont_match_with_any(iterable: "C & None & Any"):
      #                                                             └ WARNING Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances
          for _ in iterable:
              pass
      """)

    @Test
    @TestFor(issues = ["PY-85997"])
    fun `recursive iterator protocol matches Iterator`() = test(
      // Matching `() -> Iterator` against `() -> Self` is mutually recursive, so recursion prevention
      // legitimately engages while checking the assignment below.
      defaultTestOptions.copy(assertRecursionPrevention = false),
      """
      from typing import Iterator, Self

      class MyIterable[T]:
          def __next__(self) -> T: ...
          def __iter__(self) -> Self: ...

      ys: MyIterable[str]
      xs: Iterator[str] = ys
      """,
    )

    @Test
    fun `identical generic protocol and implementation using Self`() = test("""
      from typing import Self, Protocol

      class MyProtocol[T](Protocol):
          def __next__(self) -> T: ...
          def __iter__(self) -> Self: ...

      class MyIterable[T]:
          def __next__(self) -> T: ...
          def __iter__(self) -> Self: ...

      ys: MyIterable[str] = MyIterable[str]()
      xs: MyProtocol[str] = ys
      """)
  }

  @Nested
  inner class ProtocolMatchingAgainstModules {

    @Test
    @TestFor(issues = ["PY-76818"])
    fun `match module with protocol by number of attributes`() = test(
      """
      import _protocols_modules1
      from typing import Protocol


      class Options1(Protocol):
          timeout: T
      #            └ ERROR Unresolved reference 'T'
          one_flag: bool
          other_flag: bool

      class Options2(Protocol):
          timeout: str



      op1: Options1 = _protocols_modules1
      op1: Options2 = _protocols_modules1
      #│              ^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'Options2', got '_protocols_modules1' instead
      #^^ WARNING Redeclared 'op1' defined above without usage
      """,
      "_protocols_modules1.py" to """
        timeout = 100
        one_flag = True
        other_flag = False
        """,
    )

    @Test
    @TestFor(issues = ["PY-76818"])
    fun `match protocol with module callables`() = test(
      """
      import _protocols_modules2
      from typing import Protocol


      class Reporter1(Protocol):
          def on_error(self, x: int) -> None:
              ...

          def on_success(self) -> None:
              ...


      class Reporter2(Protocol):
          def on_error(self, x: int) -> int:
              ...


      class Reporter3(Protocol):
          def not_implemented(self, x: int) -> int:
              ...


      rp1: Reporter1 = _protocols_modules2  # OK
      rp2: Reporter2 = _protocols_modules2 # WARNING Expected type 'Reporter2', got '_protocols_modules2' instead
      rp3: Reporter3 = _protocols_modules2 # WARNING Expected type 'Reporter3', got '_protocols_modules2' instead
      """,
      "_protocols_modules2.py" to """
        def on_error(x: int) -> None:
            ...


        def on_success() -> None:
            ...
        """,
    )

    @Test
    @TestFor(issues = ["PY-76818"])
    fun `match generic protocol with module`() = test(
      """
      import mod
      from typing import Protocol

      class Options1[T1, T2, T3](Protocol):
          timeout: T1
          one_flag: bool
          other_flag: bool

          def foo(self, x: T1, y: T2) -> T3: ...

      t1: Options1[int, str, bool] = mod
      t2: Options1[int, float, bool] = mod # WARNING Expected type 'Options1[int, float | int, bool]', got 'mod' instead
      t3: Options1[str, float, bool] = mod # WARNING Expected type 'Options1[str, float | int, bool]', got 'mod' instead
      t4: Options1[int, str, str] = mod # WARNING Expected type 'Options1[int, str, str]', got 'mod' instead
      """,
      "mod.py" to """
        timeout = 100
        one_flag = True
        other_flag = False

        def foo(x: int, y: str) -> bool: ...
        """,
    )
  }

  @Nested
  inner class RecursiveProtocolMatchingKnownStackOverflowLimitation {

    @Test
    @TestFor(issues = ["PY-85997"])
    fun `recursive protocol and implementation using Self`() =
      fixme("Recursive protocol definitions cause infinite recursion during matching",
            StackOverflowPreventedException::class.java,
            "Endless recursion prevention occurred on") {
        test(
          """
          from typing import Self, Protocol
    
          class MyProtocol[T](Protocol):
              def __next__(self) -> T: ...
              def __iter__(self) -> MyProtocol[T]: ...
    
          class MyIterable[T]:
              def __next__(self) -> T: ...
              def __iter__(self) -> Self: ...
    
          ys: MyIterable[str] = MyIterable[str]()
          xs: MyProtocol[str] = ys
          """,
        )
      }

    @Test
    @TestFor(issues = ["PY-85997"])
    fun `recursive protocol and implementation referring to itself`() =
      fixme("Recursive protocol definitions cause infinite recursion during matching",
            StackOverflowPreventedException::class.java,
            "Endless recursion prevention occurred on") {
        test("""
          from typing import Self, Protocol
    
          class MyProtocol[T](Protocol):
              def __next__(self) -> T: ...
              def __iter__(self) -> MyProtocol[T]: ...
    
          class MyIterable[T]:
              def __next__(self) -> T: ...
              def __iter__(self) -> MyIterable[T]: ...
    
          ys: MyIterable[str] = MyIterable[str]()
          xs: MyProtocol[str] = ys
          """)
      }
  }

  @Test
  @TestFor(issues = ["PY-30357"])
  fun `nested class matched structurally on class object`() = test("""
    def f(cls):
        print(cls.Meta)

    class A:
        class Meta:
            pass

    f(A)
    """)

  @Test
  @TestFor(issues = ["PY-43133"])
  fun `inherited methods across hierarchy satisfy protocol`() = test("""
    from typing import Protocol

    class A:
        def f1(self, x: str):
            pass

    class B(A):
        def f2(self, y: str):
            pass

    class P(Protocol):
        def f1(self, x: str): ...
        def f2(self, y: str): ...

    def test(p: P):
        pass

    b = B()
    test(b)
    """)

  @Test
  @TestFor(issues = ["PY-28720"])
  fun `class overriding builtin dunder matches typing protocol`() = test("""
    import typing

    class Proto(typing.Protocol):
        def function(self) -> None:
            pass

    class Cls:
        def __eq__(self, other) -> 'Cls':
            pass

        def function(self) -> None:
            pass

    def method(p: Proto):
        pass

    method(Cls())
    """)

  @Test
  @TestFor(issues = ["PY-28720"])
  fun `matching against invalid protocol is not reported`() = test("""
    from typing import Any, Protocol

    class B:
        def foo(self):
            ...

    class C(B, Protocol): # WARNING All bases of a protocol must be protocols
        def bar(self):
            ...

    class Bar:
        def bar(self):
            ...

    def f(x: C) -> Any:
        ...

    f(Bar())
    """)

  @Test
  fun `structural types for nested calls`() = test("""
    def f(x):
        return x.foo + g(x)


    def g(x):
        return x.bar


    def test():
        f("string") # WARNING Type 'Literal["string"]' doesn't have expected attributes 'foo', 'bar'
    """)

  @Test
  fun `comparison operators for numeric types`() = test("""
    def f(x):
        print(x < 0, x <= 0, x > 0, x >= 0, x != 0)
        print(x.foo)


    print(f(True)) # WARNING Type 'Literal[True]' doesn't have expected attribute 'foo'
    print(f(0)) # WARNING Type 'Literal[0]' doesn't have expected attribute 'foo'
    print(f(3.14)) # WARNING Type 'float' doesn't have expected attribute 'foo'
    """)

  @Test
  @TestFor(issues = ["PY-27231"])
  fun `structural matching with None narrowing`() = test("""
    def func31(value):
        if value and None and value * 1:
            pass


    def func32(value):
        if value is value and value * 1:
            pass


    func31(None) # WARNING Type 'None' doesn't have expected attribute '__mul__'
    func32(None) # WARNING Type 'None' doesn't have expected attribute '__mul__'
    """)

  @Test
  @TestFor(issues = ["PY-36062"])
  fun `module object matches ModuleType and structural parameter`() = test(
    """
    import module
    from types import ModuleType

    def foo(m: ModuleType):
        pass

    def bar(m):
        return m.__name__

    foo(module)
    bar(module)
    """,
    "module.py" to "",
  )
}

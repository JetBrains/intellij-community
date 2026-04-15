// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.PyArgumentListInspection
import com.jetbrains.python.inspections.PyDataclassInspection
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for [dataclasses][https://docs.python.org/3/library/dataclasses.html]:
 * `@dataclass`, `dataclasses.field`, generated `__init__`/`__post_init__` signatures, `InitVar`,
 * frozen/order/slots semantics, descriptor-typed fields and the [dataclass_transform] mechanism.
 */
class PyDataclassTypeTest : PyCodeInsightTestCase() {

  override val defaultTestOptions =
    TestOptions(
      enablePyAnyType = false,
      assertRecursionPrevention = false,
      disableInspections = setOf(
        PyArgumentListInspection::class.java,
        PyDataclassInspection::class.java,
      )
    )

  @Nested
  inner class GeneratedInitFieldTypes {
    @Test
    @TestFor(issues = ["PY-87909"])
    fun `generic dataclass field type`() = test("""
      from dataclasses import dataclass
      
      @dataclass
      class A[T]:
          t: T
      
      expr = A(1).t
      # └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-87909"])
    fun `generic dataclass field type with legacy generic syntax`() = test("""
      from dataclasses import dataclass
      from typing import Generic, TypeVar
      
      T = TypeVar("T")
      
      
      @dataclass
      class A(Generic[T]):
          t: T
      
      
      expr = A(1).t
      #└ TYPE int
      """)
  }

  @Nested
  inner class PostInitParametersInitVar {
    @Test
    fun `dataclass post init parameter type`() = test("""
      from dataclasses import dataclass, InitVar
      @dataclass
      class Foo:
          i: int
          j: int
          d: InitVar[int]
          def __post_init__(self, d):
              expr = d
      #       └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-78006"])
    fun `dataclass post init parameters order`() = test("""
      from dataclasses import dataclass, InitVar
      
      class A1:
          pass
      class A2:
          pass
      class A3:
          pass
      class A4:
          pass
      class A5:
          pass
      class A6:
          pass
      
      @dataclass
      class BaseDC:
          b1: str
          a1: InitVar[A1]
      
      @dataclass
      class DC1(BaseDC):
          a2: InitVar[A2]
          b2: int
          a3: InitVar[A3]
      
      @dataclass
      class DC2(BaseDC):
          a4: InitVar[A4]
          b3 = 2
      
      @dataclass
      class DC(DC1, DC2):
          b4: bool
          a5: InitVar[A5]
          a6: InitVar[A6]
      
          def __post_init__(self, p1, p2, p3, p4, p5, p6):
              expr = (p1, p2, p3, p4, p5, p6)
      #       └ TYPE tuple[A1, A4, A2, A3, A5, A6]
      """)

    @Test
    @TestFor(issues = ["PY-27398"])
    fun `dataclass post init parameter with init disabled`() = test("""
      from dataclasses import dataclass, InitVar
      @dataclass(init=False)
      class Foo:
          i: int
          j: int
          d: InitVar[int]
          def __post_init__(self, d):
              expr = d
      #       └ TYPE Any
      """)

    @Test
    @TestFor(issues = ["PY-28506"])
    fun `dataclass post init inherited parameter both init`() = test("""
      from dataclasses import dataclass, InitVar
      
      @dataclass
      class A:
          a: InitVar[int]
      
      @dataclass
      class B(A):
          b: InitVar[str]
      
          def __post_init__(self, a, b):
              expr = a
      #       └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-28506"])
    fun `dataclass post init inherited parameter derived no init`() = test("""
      from dataclasses import dataclass, InitVar
      
      @dataclass
      class A:
          a: InitVar[int]
      
      @dataclass(init=False)
      class B(A):
          b: InitVar[str]
      
          def __post_init__(self, a, b):
              expr = a
      #       └ TYPE Any
      """)

    @Test
    @TestFor(issues = ["PY-28506"])
    fun `dataclass post init inherited parameter base no init`() = test("""
      from dataclasses import dataclass, InitVar
      
      @dataclass(init=False)
      class A:
          a: InitVar[int]
      
      @dataclass
      class B(A):
          b: InitVar[str]
      
          def __post_init__(self, a, b):
              expr = a
      #       └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-28506"])
    fun `dataclass post init inherited parameter both no init`() = test("""
      from dataclasses import dataclass, InitVar
      
      @dataclass(init=False)
      class A:
          a: InitVar[int]
      
      @dataclass(init=False)
      class B(A):
          b: InitVar[str]
      
          def __post_init__(self, a, b):
              expr = a
      #       └ TYPE Any
      """)

    @Test
    @TestFor(issues = ["PY-28506"])
    fun `mixed dataclass post init inherited parameter base plain`() = test("""
      from dataclasses import dataclass, InitVar
      
      class A:
          a: InitVar[int]
      
      @dataclass
      class B(A):
          b: InitVar[str]
      
          def __post_init__(self, a, b):
              expr = a
      #       └ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-28506"])
    fun `mixed dataclass post init inherited parameter derived plain`() = test("""
      from dataclasses import dataclass, InitVar
      
      @dataclass
      class A:
          a: InitVar[int]
      
      class B(A):
          b: InitVar[str]
      
          def __post_init__(self, a, b):
              expr = a
      #       └ TYPE Any
      """)
  }

  @Nested
  inner class SlotsDisjointBase {
    @Test
    @TestFor(issues = ["PY-83206"])
    fun `dataclass with slots creates disjoint base`() = test("""
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
  }

  @Nested
  inner class DataclassTransformConstructorSignature {
    @Test
    fun `dataclass_transform constructor signature`() = test("""
      from typing import dataclass_transform
      
      @dataclass_transform()
      def deco(cls):
          ...
      
      @deco
      class MyClass:
           id: int
           name: str
      
      MyClass()
      #└ TYPE (id: int, name: str) -> MyClass
      """)

    @Test
    fun `dataclass_transform constructor signature decorated base class attribute excluded`() = test("""
      from typing import dataclass_transform
      
      @dataclass_transform()
      class Base:
          excluded: int
      
      class Sub(Base):
          id: int
      
      class SubSub(Sub):
           name: str
      
      SubSub()
      # └ TYPE (id: int, name: str) -> SubSub
      """)

    @Test
    fun `dataclass_transform constructor signature metaclass base class attribute not excluded`() = test("""
      from typing import dataclass_transform
      
      @dataclass_transform()
      class Meta(type):
          pass
      
      class Base(metaclass=Meta):
          included: int
      
      class Sub(Base):
          id: int
      
      class SubSub(Sub):
          name: str
      
      SubSub()
      #└ TYPE (included: int, id: int, name: str) -> SubSub
      """)

    @Test
    fun `dataclass_transform overloads`() = test("""
      from typing import dataclass_transform, overload
      
      @overload
      def deco(name: str):
          ...
      
      
      @dataclass_transform()
      @overload
      def deco(cls: type):
          ...
      
      @overload
      def deco():
          ...
      
      def deco(*args, **kwargs):
          ...
      
      @deco
      class MyClass:
           id: int
           name: str
      
      MyClass()
      #└ TYPE (id: int, name: str) -> MyClass
      """)

    @Test
    fun `dataclass_transform own kw_only omitted and taken from kw_only_default`() = test("""
      from typing import dataclass_transform, Callable
      
      
      @dataclass_transform(kw_only_default=True)
      def deco(**kwargs) -> Callable[[type], type]:
          ...
      
      
      @deco(frozen=True)
      class MyClass:
          id: int
          name: str
      
      
      MyClass()
      #└ TYPE (*, id: int, name: str) -> MyClass
      """)

    @Test
    fun `dataclass_transform field specifier kw_only default overrides decorators kw_only`() = test("""
      from typing import Callable, dataclass_transform
      
      def my_field(kw_only=False):
          ...
      
      @dataclass_transform(field_specifiers=(my_field,))
      def my_dataclass(**kwargs) -> Callable[[type], type]:
          ...
      
      @my_dataclass(kw_only=True)
      class Order:
          id: str = my_field() # WARNING Expected type 'str', got 'None' instead
          addr: list[str]
      
      Order()
      # └ TYPE (id: str, *, addr: list[str]) -> Order
      """)

    @Test
    fun `dataclass_transform field specifier kw_only default overrides decorators kw_only_default`() = test("""
      from typing import Callable, dataclass_transform
      
      def my_field(kw_only=False):
          ...
      
      @dataclass_transform(kw_only_default=True, field_specifiers=(my_field,))
      def my_dataclass(**kwargs) -> Callable[[type], type]:
          ...
      
      @my_dataclass()
      class Order:
          id: str = my_field() # WARNING Expected type 'str', got 'None' instead
          addr: list[str]
      
      Order()
      #└ TYPE (id: str, *, addr: list[str]) -> Order
      """)

    @Test
    fun `dataclass_transform field specifier kw_only overrides decorators kw_only`() = test("""
      from typing import Callable, dataclass_transform
      
      def my_field(kw_only=False):
          ...
      
      @dataclass_transform(field_specifiers=(my_field,))
      def my_dataclass(**kwargs) -> Callable[[type], type]:
          ...
      
      @my_dataclass(kw_only=True)
      class Order:
          id: str = my_field(kw_only=False) # WARNING Expected type 'str', got 'None' instead
          addr: list[str]
      
      Order()
      #└ TYPE (id: str, *, addr: list[str]) -> Order
      """)

    @Test
    fun `dataclass_transform field specifier kw_only overrides decorators kw_only_default`() = test("""
      from typing import Callable, dataclass_transform
      
      def my_field(kw_only=False):
          ...
      
      @dataclass_transform(kw_only_default=True, field_specifiers=(my_field,))
      def my_dataclass(**kwargs) -> Callable[[type], type]:
          ...
      
      @my_dataclass()
      class Order:
          id: str = my_field(kw_only=False) # WARNING Expected type 'str', got 'None' instead
          addr: list[str]
      
      Order()
      #└ TYPE (id: str, *, addr: list[str]) -> Order
      """)

    @Test
    fun `dataclass_transform field specifier overload init false constructor signature`() = test("""
      from typing import Any, Callable, Literal, TypeVar, dataclass_transform, overload
      
      T = TypeVar("T")
      
      
      @overload
      def field1(
              *,
              # default: str | None = None,
              resolver: Callable[[], Any],
              init: Literal[False] = False,
      ) -> Any:
          ...
      
      @overload
      def field1(
              *,
              init: Literal[True] = True,
              kw_only: bool = True,
              default: Any = None
      ) -> Any:
          ...
      
      def field1(**kwargs) -> Any:
          return kwargs
      
      
      @dataclass_transform(kw_only_default=True, field_specifiers=(field1,))
      def create_model(*, init: bool = True) -> Callable[[type[T]], type[T]]:
          ...
      
      
      @create_model()
      class CustomerModel1:
          id: int = field1(resolver=lambda: 0)
          name: str = field1(default="Voldemort")
      
      CustomerModel1()
      #└ TYPE (*, name: str) -> CustomerModel1
      """)

    @Test
    fun `dataclass_transform decorated function type`() = test("""
      from typing import dataclass_transform
      
      @dataclass_transform()
      def my_dataclass(cls): ...
      
      expr = my_dataclass
      #└ TYPE (cls: Any) -> None
      """)

    @Test
    fun `dataclass_transform constructor signature with fields annotated with descriptor`() = test("""
      from typing import dataclass_transform
      
      @dataclass_transform()
      def deco(cls):
          ...
      
      class MyIdDescriptor:
          def __set__(self, obj: object, value: int) -> None:
              ...
      
      class MyNameDescriptor:
          def __set__(self, obj: object, value: str) -> None:
              ...
      
      @deco
      class MyClass:
           id: MyIdDescriptor
           name: MyNameDescriptor
      
      MyClass()
      #└ TYPE (id: int, name: str) -> MyClass
      """)

    @Test
    @TestFor(issues = ["PY-76149"])
    fun `dataclass_transform constructor signature with fields annotated with generic descriptor`() = test("""
      from typing import dataclass_transform, TypeVar, Generic
      
      T = TypeVar("T")
      
      @dataclass_transform()
      def deco(cls):
          ...
      
      class MyDescriptor(Generic[T]):
          def __set__(self, obj: object, value: T) -> None:
              ...
      
      @deco
      class MyClass:
           id: MyDescriptor[int]
           name: MyDescriptor[str]
      
      MyClass(1, "")
      #└ TYPE (id: int, name: str) -> MyClass
      """)

    @Test
    @TestFor(issues = ["PY-76149"])
    fun `dataclass_transform constructor signature with fields annotated with explicit Any`() = test("""
      from typing import dataclass_transform, TypeVar, Generic, Any
      
      T = TypeVar("T")
      
      @dataclass_transform()
      def deco(cls):
          ...
      
      class MyDescriptor(Generic[T]):
          def __set__(self, obj: object, value: T) -> None:
              ...
      
      class Anything:
          def __set__(self, obj: object, value: Any) -> None:
              ...
      
      @deco
      class MyClass:
          id: MyDescriptor[int]
          name: MyDescriptor[str]
          payload: Anything
          payload_length: MyDescriptor[int]
      
      MyClass()
      #└ TYPE (id: int, name: str, payload: Any, payload_length: int) -> MyClass
      """)

    @Test
    @TestFor(issues = ["PY-88828"])
    fun `dataclass_transform constructor signature decorator with marked overloads not implementation`() = test(
      """
      from mod import Document
      
      Document(name="foo")
      #└ TYPE (*, name: str) -> Document
      """,
      "mod.py" to """
        import strawberry
        
        
        @strawberry.type
        class Document:
            name: str
        """,
      "strawberry.py" to """
        from typing import overload, dataclass_transform, Sequence, Callable
        
        
        def field():
            pass
        
        
        class StrawberryField:
            pass
        
        
        @overload
        @dataclass_transform(
            order_default=True, kw_only_default=True, field_specifiers=(field, StrawberryField)
        )
        def type[T](
                cls: T,
                *,
                name: str | None = None,
                is_input: bool = False,
                is_interface: bool = False,
                description: str | None = None,
                directives: Sequence[object] | None = (),
                extend: bool = False,
        ) -> T: ...
        
        
        @overload
        @dataclass_transform(
            order_default=True, kw_only_default=True, field_specifiers=(field, StrawberryField)
        )
        def type[T](
                *,
                name: str | None = None,
                is_input: bool = False,
                is_interface: bool = False,
                description: str | None = None,
                directives: Sequence[object] | None = (),
                extend: bool = False,
        ) -> Callable[[T], T]: ...
        
        
        def type[T](
                cls: T | None = None,
                *,
                name: str | None = None,
                is_input: bool = False,
                is_interface: bool = False,
                description: str | None = None,
                directives: Sequence[object] | None = (),
                extend: bool = False,
        ) -> T | Callable[[T], T]:
            pass
        """,
    )
  }

  @Nested
  inner class InitArgumentTypeChecking {
    @Test
    @TestFor(issues = ["PY-27398"])
    fun `initializing dataclass checks generated init arguments`() = test("""
      import dataclasses
      import typing

      @dataclasses.dataclass
      class A:
          x: int
          y: str
          z: float = 0.0

      A(1, "a")
      A("a", 1)
      # │    └ WARNING Expected type 'str', got 'Literal[1]' instead
      # ^^^ WARNING Expected type 'int', got 'Literal["a"]' instead

      A(1, "a", 1.0)
      A("a", 1, "b")
      # │    │  ^^^ WARNING Expected type 'float | int', got 'Literal["b"]' instead
      # │    └ WARNING Expected type 'str', got 'Literal[1]' instead
      # ^^^ WARNING Expected type 'int', got 'Literal["a"]' instead


      @dataclasses.dataclass(init=True)
      class A2:
          x: int
          y: str
          z: float = 0.0

      A2(1, "a")
      A2("a", 1)
      #  │    └ WARNING Expected type 'str', got 'Literal[1]' instead
      #  ^^^ WARNING Expected type 'int', got 'Literal["a"]' instead

      A2(1, "a", 1.0)
      A2("a", 1, "b")
      #  │    │  ^^^ WARNING Expected type 'float | int', got 'Literal["b"]' instead
      #  │    └ WARNING Expected type 'str', got 'Literal[1]' instead
      #  ^^^ WARNING Expected type 'int', got 'Literal["a"]' instead


      @dataclasses.dataclass(init=False)
      class B1:
          x: int = 1
          y: str = "2"
          z: float = 0.0

      B1(1)
      B1("1")

      B1(1, "a")
      B1("a", 1)

      B1(1, "a", 1.0)
      B1("a", 1, "b")


      @dataclasses.dataclass(init=False)
      class B2:
          x: int
          y: str
          z: float = 0.0

          def __init__(self, x: int):
              self.x = x
              self.y = str(x)
              self.z = 0.0

      B2(1)
      B2("1") # WARNING Expected type 'int', got 'Literal["1"]' instead


      @dataclasses.dataclass
      class C1:
          a: typing.ClassVar[int]
          b: int

      C1(1)
      C1("1") # WARNING Expected type 'int', got 'Literal["1"]' instead


      @dataclasses.dataclass
      class C2:
          a: typing.ClassVar
          b: int

      C2(1)
      C2("1") # WARNING Expected type 'int', got 'Literal["1"]' instead


      @dataclasses.dataclass
      class D1:
          a: dataclasses.InitVar[int]
          b: int

      D1(1, 2)
      D1("1", "2")
      #  │    ^^^ WARNING Expected type 'int', got 'Literal["2"]' instead
      #  ^^^ WARNING Expected type 'int', got 'Literal["1"]' instead


      @dataclasses.dataclass
      class E1:
          a: int = dataclasses.field()
          b: str = dataclasses.field(init=True)
          c: int = dataclasses.field(init=False)
          d: bytes = dataclasses.field(default=b"b")
          e: int = dataclasses.field(default_factory=int)

      E1(1, "1")
      E1("1", 1)
      #  │    └ WARNING Expected type 'str', got 'Literal[1]' instead
      #  ^^^ WARNING Expected type 'int', got 'Literal["1"]' instead

      E1(1, "1", b"1")
      E1(b"1", "1", 1)
      #  │          └ WARNING Expected type 'bytes', got 'Literal[1]' instead
      #  ^^^^ WARNING Expected type 'int', got 'bytes' instead

      E1(1, "1", b"1", 1)
      E1("1", b"1", "1", "1")
      #  │    │     │    ^^^ WARNING Expected type 'int', got 'Literal["1"]' instead
      #  │    │     ^^^ WARNING Expected type 'bytes', got 'Literal["1"]' instead
      #  │    ^^^^ WARNING Expected type 'str', got 'bytes' instead
      #  ^^^ WARNING Expected type 'int', got 'Literal["1"]' instead


      @dataclasses.dataclass
      class F1:
          foo = "bar"  # <- has no type annotation, so doesn't count.
          baz: str

      F1("1")
      F1(1) # WARNING Expected type 'str', got 'Literal[1]' instead
      """)

    @Test
    @TestFor(issues = ["PY-28442"])
    fun `dataclass cls call type does not trigger false positive`() = test("""
      from dataclasses import dataclass
      
      
      @dataclass
      class Point:
          x: int
          y: int
      
          @classmethod
          def from_str(cls, string: str) -> 'Point':
              return cls(1, 2)
      """)

    @Test
    @TestFor(issues = ["PY-36889"])
    fun `dataclass instance attribute assignment is checked`() = test("""
      from dataclasses import dataclass
      
      @dataclass
      class C:
          attr: int = 1
      
      C().attr = "foo" # WARNING Expected type 'int', got 'Literal["foo"]' instead
      """)
  }

  @Nested
  inner class DataclassInstanceProtocolStructuralMatching {
    @Test
    @TestFor(issues = ["PY-76059"])
    fun `dataclass instance matches DataclassInstance protocol`() = test("""
      from dataclasses import dataclass, asdict
      
      @dataclass
      class MyDataClass:
          name:str
      
      asdict(MyDataClass(name="Bob"))
      asdict("Bob") # WARNING Expected type 'DataclassInstance', got 'Literal["Bob"]' instead
      """)
  }

  @Nested
  inner class HashabilityFrozenEqUnsafeHash {
    @Test
    @TestFor(issues = ["PY-76854"])
    fun `non-hashable dataclass assigned to Hashable`() = test("""
      from dataclasses import dataclass
      from typing import Hashable
      
      
      @dataclass
      class DC:
          a: int
      
      
      v: Hashable = DC(0) # WARNING Expected type 'Hashable', got 'DC' instead
      
      @dataclass(eq=True)
      class DC2:
          a: int
      
      
      v2: Hashable = DC2(0) # WARNING Expected type 'Hashable', got 'DC2' instead
      """)

    @Test
    @TestFor(issues = ["PY-76854"])
    fun `hashable dataclass assigned to Hashable`() = test("""
      from dataclasses import dataclass
      from typing import Hashable
      
      
      @dataclass(eq=True, frozen=True)
      class DC:
          a: int
      
      
      v: Hashable = DC(0)
      
      @dataclass(eq=True)
      class DC2:
          a: int
      
          def __hash__(self) -> int:
              return 0
      
      
      v2: Hashable = DC2(0)
      
      @dataclass(unsafe_hash=True)
      class DC3:
          a: int
      
      
      v3: Hashable = DC3(0)
      
      @dataclass(eq=False, frozen=True)
      class DC4:
          a: int
      
      
      v4: Hashable = DC4(0)
      
      @dataclass(eq=False)
      class DC5:
          a: int
      
      
      v5: Hashable = DC5(0)
      """)
  }

  @Nested
  inner class OrderTrue {
    @Test
    @TestFor(issues = ["PY-45958"])
    fun `ordered dataclass can be sorted`() = test("""
      from dataclasses import dataclass
      
      @dataclass(order=True)
      class DC: ...
      
      sorted([DC(), DC()])
      """)

    @Test
    @TestFor(issues = ["PY-45958"])
    fun `ordered dataclass implements less-than protocol`() = test("""
      from dataclasses import dataclass
      from typing import Any, Protocol
      
      class SupportsLessThan(Protocol):
          def __lt__(self, other: Any) -> bool: ...
      
      @dataclass(order=True)
      class DC: ...
      
      a: SupportsLessThan = DC()
      """)

    @Test
    @TestFor(issues = ["PY-45958"])
    fun `ordered dataclass dunder le call is type-checked`() = test("""
      from dataclasses import dataclass
      
      @dataclass(order=True)
      class A: ...
      
      @dataclass(order=True)
      class B: ...
      
      A().__le__(A())
      A().__le__(B()) # WARNING Expected type 'A', got 'B' instead
      """)
  }

  @Nested
  inner class FieldDefaultFactory {
    @Test
    @TestFor(issues = ["PY-76861"])
    fun `field default_factory return type is checked against field type`() = test("""
      from dataclasses import dataclass, field
      
      @dataclass
      class E:
          a: int = field(default_factory=(lambda: "")) # WARNING Expected type 'int', got 'str' instead
      """)

    @Test
    @TestFor(issues = ["PY-76861"])
    fun `field default_factory return type for function reference`() = test("""
      from dataclasses import dataclass, field
      from typing import Callable
      
      def make_str() -> str:
          return "hello"
      
      @dataclass
      class A:
          x: int = field(default_factory=make_str) # WARNING Expected type 'int', got 'str' instead
      """)

    @Test
    @TestFor(issues = ["PY-76861"])
    fun `field default_factory return type for call returning factory`() = test("""
      from dataclasses import dataclass, field
      from typing import Callable
      
      def make_factory() -> Callable[[], str]:
          def inner() -> str:
              return "hello"
          return inner
      
      @dataclass
      class B:
          y: int = field(default_factory=make_factory()) # WARNING Expected type 'int', got 'str' instead
      """)

    @Test
    @TestFor(issues = ["PY-88042"])
    fun `field default_factory union type`() = test("""
      from dataclasses import dataclass, field
      
      @dataclass
      class DC:
          a: str | None = field(default_factory=lambda: "")
      """)

    @Test
    @TestFor(issues = ["PY-88043"])
    fun `field default_factory returns Any`() = test("""
      from dataclasses import dataclass, field
      from typing import Any
      
      def factory() -> Any:
          pass
      
      @dataclass
      class DC:
          a: str | None = field(default_factory=factory)
      """)

    @Test
    @TestFor(issues = ["PY-88043"])
    fun `field default_factory not annotated multifile`() = test(
      """
      from dataclasses import dataclass, field
      from lib import do
      
      @dataclass
      class NewDataclass:
          val: str | None = field(default_factory=do)
      """,
      "lib.py" to """
        def do():
            return "this or that"
        """,
    )

    @Test
    @TestFor(issues = ["PY-76861"])
    fun `field default_factory returns class object`() = test("""
      from dataclasses import dataclass, field
      
      @dataclass
      class DC:
          a: str = field(default_factory=(lambda: str)) # WARNING Expected type 'str', got 'type[str]' instead
      """)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

/**
 * The override check compares a method with the base method after it substitutes the type arguments
 * that the subclass gives to its generic ancestors.
 */
@TestFor(classes = [PyMethodOverridingInspection::class], issues = ["PY-41343"])
@Subsystems.Inspections
@Layers.Functional
class PyGenericBaseMethodOverrideTest : PyCodeInsightTestCase() {

  @Test
  fun `return type incompatible with the type argument of the base class`() = test("""
    class Base[T]:
        def x(self) -> T: ...

    class Child(Base[int]):
        def x(self) -> str:
    #                  ^^^ WARNING Return type of method 'Child.x()' does not match return type the base method in class 'Base'
            return "abc"
    """)

  @TestFor(issues = ["PY-94116"])
  @Test
  fun `async return type incompatible with the type argument of the base class old style`() = test("""
    from abc import ABC, abstractmethod
    from typing import Generic, TypeVar

    TCommand = TypeVar("TCommand", bound="Command")
    TResponse = TypeVar("TResponse")

    class Command(ABC, Generic[TResponse]):
        pass

    class CommandHandler(ABC, Generic[TCommand, TResponse]):
        @abstractmethod
        async def handle(self, command: TCommand) -> TResponse:
            pass

    class CreateUserCommand(Command[int]):
        pass

    class CreateUserCommandHandler(CommandHandler[CreateUserCommand, int]):
        async def handle(self, command: CreateUserCommand) -> str:
    #                                                         ^^^ WARNING Return type of method 'CreateUserCommandHandler.handle()' does not match return type the base method in class 'CommandHandler'
            return "123"
    """)

  @Test
  fun `return type that matches the type argument of the base class`() = test("""
    class Base[T]:
        def x(self) -> T: ...

    class Child(Base[int]):
        def x(self) -> int:
            return 1
    """)

  @Test
  fun `return type that is a subtype of the type argument of the base class`() = test("""
    class Base[T]:
        def x(self) -> T: ...

    class Child(Base[object]):
        def x(self) -> int:
            return 1
    """)

  // The bound violation in the base class list is the only error. The override is consistent with `CommandHandler[CreateUserCommand, int]`.
  @Test
  fun `type argument that violates the bound does not make the override incompatible`() = test("""
    from abc import ABC, abstractmethod

    class Command[TResponse](ABC):
        pass

    class CommandHandler[TCommand, TResponse: Command](ABC):
        @abstractmethod
        async def handle(self, command: TCommand) -> TResponse:
            pass

    class CreateUserCommand(Command[int]):
        pass

    class CreateUserCommandHandler(CommandHandler[CreateUserCommand, int]):
    #                                                                ^^^ WARNING Expected type 'TResponse ≤: Command', got 'int' instead
        async def handle(self, command: CreateUserCommand) -> int:
            return 123
    """)

  @TestFor(issues = ["PY-49011"])
  @Test
  fun `parameter type incompatible with the type argument of the base class`() = test("""
    class Fooable[T]:
        def fooit(self, val: T) -> None:
            raise NotImplementedError()

    class Foo(Fooable[str]):
        def fooit(self, val: int) -> None:
    #            ^^^^^^^^^^^^^^^^ WARNING Signature of method 'Foo.fooit()' does not match signature of the base method in class 'Fooable'
            print("foo")
    """)

  @Test
  fun `parameter type that is a supertype of the type argument of the base class`() = test("""
    class Base[T]:
        def f(self, val: T) -> None: ...

    class Child(Base[int]):
        def f(self, val: object) -> None: ...
    """)

  @Test
  fun `type argument given by an intermediate class`() = test("""
    class Base[T]:
        def x(self) -> T: ...

    class Middle(Base[int]):
        pass

    class Leaf(Middle):
        def x(self) -> str:
    #                  ^^^ WARNING Return type of method 'Leaf.x()' does not match return type the base method in class 'Base'
            return ""
    """)

  @Test
  fun `type argument that is a type parameter of the subclass`() = test("""
    class Base[T]:
        def x(self, val: T) -> T: ...

    class Child[S](Base[S]):
        def x(self, val: S) -> S:
            return val
    """)

  @Test
  fun `type argument that is a parameterized type`() = test("""
    class Base[T]:
        def x(self) -> list[T]: ...

    class Child(Base[int]):
        def x(self) -> list[str]:
    #                  ^^^^^^^^^ WARNING Return type of method 'Child.x()' does not match return type the base method in class 'Base'
            return []
    """)

  @Test
  fun `base class in another file`() = test("""
    from base import Base

    class Child(Base[int]):
        def x(self, val: int) -> str:
    #                            ^^^ WARNING Return type of method 'Child.x()' does not match return type the base method in class 'Base'
            return ""
    """, "base.py" to """
    class Base[T]:
        def x(self, val: T) -> T: ...
    """.trimIndent())

  @Test
  fun `base class without type arguments`() = test("""
    class Base[T]:
        def x(self) -> T: ...

    class Child(Base):
        def x(self) -> str:
            return ""
    """)

  @Test
  fun `type parameter of the base method`() = test("""
    class Base[T]:
        def x[U](self, val: U) -> U: ...

    class Child(Base[int]):
        def x[V](self, val: V) -> V:
            return val
    """)

  @Test
  fun `ParamSpec type argument`() = test("""
    class Base[**P]:
        def call(self, *args: P.args, **kwargs: P.kwargs) -> None: ...

    class Child(Base[[int, str]]):
        def call(self, a: int, b: str) -> None: ...
    """)

  @Test
  fun `TypeVarTuple type argument`() = test("""
    class Base[*Ts]:
        def call(self, *args: *Ts) -> None: ...

    class Child(Base[int, str]):
        def call(self, *args: *tuple[int, str]) -> None: ...
    """)

  @Test
  fun `type argument of a typeshed generic class`() = test("""
    from collections.abc import Iterator

    class Numbers(Iterator[int]):
        def __next__(self) -> str:
    #                         ^^^ WARNING Return type of method 'Numbers.__next__()' does not match return type the base method in class 'Iterator'
            return ""
    """)

  @TestFor(issues = ["PY-88976"])
  @Test
  fun `overloaded override of a method of a generic base class`() = test("""
    from typing import overload

    class Base[T]:
        def f(self, x: T) -> T: ...

    class Compatible(Base[int]):
        @overload
        def f(self, x: int) -> int: ...
        @overload
        def f(self, x: str) -> str: ...
        def f(self, x: int | str) -> int | str:
            return x

    class Incompatible(Base[int]):
        @overload
        def f(self, x: str) -> str: ...
        @overload
        def f(self, x: bytes) -> bytes: ...
        def f(self, x: str | bytes) -> str | bytes:
    #        ^^^^^^^^^^^^^^^^^^^^^^ WARNING Signature of method 'Incompatible.f()' does not match signature of the base method in class 'Base'
            return x
    """)
}

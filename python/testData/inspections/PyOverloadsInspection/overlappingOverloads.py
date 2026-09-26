from typing import Iterable, overload, Any, Literal

class TestIterable:
    @overload
    def func(self, seq: Iterable[str]) -> list[str]:
        ...

    @overload
    def <warning descr="This overload will never be matched as parameter types(s) of overload 1 are the same or broaderConflicting signature: '(self: Self@TestIterable, seq: str) -> str'">func</warning>(self, seq: str) -> str: ...

    def func(self, seq: str | Iterable[str]) -> str | list[str]:
        return "something"

class Animal: ...
class Dog(Animal): ...

class testDirectSubclass:
    @overload
    def foo(self, a: Animal) -> str: ...

    @overload
    def <warning descr="This overload will never be matched as parameter types(s) of overload 1 are the same or broaderConflicting signature: '(self: Self@testDirectSubclass, a: Dog) -> int'">foo</warning>(self, a: Dog) -> int: ...

    def foo(self, a: Any) -> Any: ...

class TestUnion:
    @overload
    def foo(self, a: str | int) -> bytes: ...

    @overload
    def <warning descr="This overload will never be matched as parameter types(s) of overload 1 are the same or broaderConflicting signature: '(self: Self@TestUnion, a: str) -> str'">foo</warning>(self, a: str) -> str: ...

    def foo(self, a: Any) -> Any: ...

class TestWithDefaults:
    @overload
    def foo(self, a: int, b: str = ...) -> int: ...

    @overload
    def <warning descr="This overload will never be matched as parameter types(s) of overload 1 are the same or broaderConflicting signature: '(self: Self@TestWithDefaults, a: int) -> str'">foo</warning>(self, a: int) -> str: ...

    def foo(self, a: int, b=None): ...

class Desc1:
    @overload
    def __get__(self, __obj: object, __owner: Any) -> int:
        ...

    @overload
    def __get__(self, __obj: None, __owner: Any) -> "Desc1":
        ...


    def __get__(self, __obj: object | None, __owner: Any) -> "int | Desc1":
        raise NotImplementedError

    def __set__(self, __obj: object, __value: int) -> None:
        ...
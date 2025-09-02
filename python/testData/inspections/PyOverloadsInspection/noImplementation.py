from abc import ABC, abstractmethod
from typing import overload, Protocol


@overload
def <warning descr="A series of @overload-decorated functions should always be followed by an implementation that is not @overload-ed">foo</warning>(value: None) -> None:
    pass


@overload
def foo(value: int) -> str:
    pass


@overload
def foo(value: str) -> str:
    pass


class A:
    @overload
    def <warning descr="A series of @overload-decorated methods should always be followed by an implementation that is not @overload-ed">foo</warning>(self, value: None) -> None:
        pass

    @overload
    def foo(self, value: int) -> str:
        pass

    @overload
    def foo(self, value: str) -> str:
        pass


class P(Protocol):
    @overload
    def foo(self, x: int) -> int:
        pass

    @overload
    def foo(self, x: str) -> str:
        pass


class Abstract(ABC):
    @overload
    @abstractmethod
    def foo(self, x: int) -> int:
        pass

    @overload
    @abstractmethod
    def foo(self, x: str) -> str:
        pass

    @overload
    def <warning descr="A series of @overload-decorated methods should always be followed by an implementation that is not @overload-ed">not_abstract</warning>(self, x: int) -> int:
        pass

    @overload
    def not_abstract(self, x: str) -> str:
        pass
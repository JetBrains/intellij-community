from typing import overload


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
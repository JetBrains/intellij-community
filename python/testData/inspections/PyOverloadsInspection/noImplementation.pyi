from typing import overload


@overload
def foo(value: None) -> None:
    pass


@overload
def foo(value: int) -> str:
    pass


@overload
def foo(value: str) -> str:
    pass


class A:
    @overload
    def foo(self, value: None) -> None:
        pass

    @overload
    def foo(self, value: int) -> str:
        pass

    @overload
    def foo(self, value: str) -> str:
        pass

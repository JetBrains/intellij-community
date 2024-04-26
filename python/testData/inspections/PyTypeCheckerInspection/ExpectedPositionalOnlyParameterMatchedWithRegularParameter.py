from typing import Protocol


class MyProtocol(Protocol):
    def method(self, param, /) -> None:
        pass


def f(xs: MyProtocol):
    pass


class MyClass:
    def method(self, param) -> None:
        pass


f(MyClass())

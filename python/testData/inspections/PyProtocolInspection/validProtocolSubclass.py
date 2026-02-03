from typing import Protocol


class MyProtocol(Protocol):
    attr: int

    def func(self, p: int) -> str:
        pass


class MyClass1(MyProtocol):
    def __init__(self, attr: int) -> None:
        self.attr = attr


    def func(self, p: int) -> str:
        pass


class MyClass2(MyProtocol):
    attr: int

    def func(self, p: int) -> str:
        pass


class HisProtocol(MyProtocol, Protocol):
    attr: int

    def func(self, p: int) -> str:
        pass
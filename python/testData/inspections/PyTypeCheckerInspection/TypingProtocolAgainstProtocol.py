from typing import Protocol


class MyProtocol1(Protocol):
    attr: int

    def func(self, p: int) -> str:
        pass


class MyProtocol2(Protocol):
    attr: int
    more_attr: int

    def func(self, p: int) -> str:
        pass

    def more_func(self, p: str) -> int:
        pass


class MyProtocol3(Protocol):
    attr: str
    more_attr: str

    def func(self, p: str) -> int:
        pass

    def more_func(self, p: int) -> str:
        pass


def foo(p: MyProtocol1):
    pass


v1: MyProtocol2
v2: MyProtocol3

foo(v1)
foo(<warning descr="Expected type 'MyProtocol1', got 'MyProtocol3' instead">v2</warning>)

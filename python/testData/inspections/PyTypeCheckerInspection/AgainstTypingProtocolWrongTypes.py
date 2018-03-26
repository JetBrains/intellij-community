from typing import Protocol


class MyProtocol(Protocol):
    attr: int
    def func(self, p: int) -> str:
        pass


class MyClass1:
    def __init__(self, attr: int) -> None:
        self.attr = attr


    def func(self, p: str) -> int:
        pass


class MyClass2:
    def __init__(self, attr: str) -> None:
        self.attr = attr


    def func(self, p: int) -> str:
        pass


class MyClass3:
    def __init__(self, attr: str) -> None:
        self.attr = attr


    def func(self, p: str) -> int:
        pass


def foo(m: MyProtocol):
    pass


foo(<warning descr="Expected type 'MyProtocol', got 'MyClass1' instead">MyClass1(1)</warning>)
foo(<warning descr="Expected type 'MyProtocol', got 'MyClass2' instead">MyClass2("1")</warning>)
foo(<warning descr="Expected type 'MyProtocol', got 'MyClass3' instead">MyClass3("1")</warning>)
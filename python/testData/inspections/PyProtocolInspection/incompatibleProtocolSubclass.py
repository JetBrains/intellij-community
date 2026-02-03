from typing import Protocol


class MyProtocol(Protocol):
    attr: int

    def func(self, p: int) -> str:
        pass


class MyClass1(MyProtocol):
    def __init__(self, attr: int) -> None:
        self.attr = attr

    def <warning descr="Type of 'func' is incompatible with 'MyProtocol'">func</warning>(self, p: str) -> int:
        pass


class MyClass2(MyProtocol):
    def __init__(self, attr: str) -> None:
        self.attr = attr  # mypy says nothing

    def func(self, p: int) -> str:
        pass


class MyClass3(MyProtocol):
    def __init__(self, attr: str) -> None:
        self.attr = attr  # mypy says nothing

    def <warning descr="Type of 'func' is incompatible with 'MyProtocol'">func</warning>(self, p: str) -> int:
        pass


class MyClass4(MyProtocol):
    attr: int

    def <warning descr="Type of 'func' is incompatible with 'MyProtocol'">func</warning>(self, p: str) -> int:
        pass


class MyClass5(MyProtocol):
    <warning descr="Type of 'attr' is incompatible with 'MyProtocol'">attr</warning>: str

    def func(self, p: int) -> str:
        pass


class MyClass6(MyProtocol):
    <warning descr="Type of 'attr' is incompatible with 'MyProtocol'">attr</warning>: str

    def <warning descr="Type of 'func' is incompatible with 'MyProtocol'">func</warning>(self, p: str) -> int:
        pass


class HisProtocol(MyProtocol, Protocol):
    <warning descr="Type of 'attr' is incompatible with 'MyProtocol'">attr</warning>: str

    def <warning descr="Type of 'func' is incompatible with 'MyProtocol'">func</warning>(self, p: str) -> int:
        pass

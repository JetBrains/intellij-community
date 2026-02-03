from typing import Protocol
from abc import abstractmethod


class MethodExample(Protocol):
    def first(self) -> int:
        return 42

    @abstractmethod
    def second(self) -> int:
        raise NotImplementedError


class MethodExampleImpl1:
    def first(self) -> int:
        return 42

    def second(self) -> int:
        return 24


class MethodExampleImpl2(MethodExample):
    def second(self) -> int:
        return 24


class MethodExampleImpl3:
    def second(self) -> int:
        return 24


def example(e: MethodExample) -> None:
    print(e.first())
    print(e.second())


example(MethodExampleImpl1())
example(MethodExampleImpl2())
example(<warning descr="Expected type 'MethodExample', got 'MethodExampleImpl3' instead">MethodExampleImpl3()</warning>)

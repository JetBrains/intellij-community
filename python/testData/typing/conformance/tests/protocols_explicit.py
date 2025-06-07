"""
Tests the handling of explicit protocol classes.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/protocol.html#explicitly-declaring-implementation

from abc import ABC, abstractmethod
from typing import ClassVar, Protocol


class PColor(Protocol):
    @abstractmethod
    def draw(self) -> str:
        ...

    def complex_method(self) -> int:
        return 1


class NiceColor(PColor):
    def draw(self) -> str:
        return "deep blue"


class BadColor(PColor):
    def draw(self) -> str:
        return super().draw()  # E: no default implementation


class ImplicitColor:  # Note no 'PColor' base here
    def draw(self) -> str:
        return "probably gray"

    def complex_method(self) -> int:
        return 0


v1: PColor = NiceColor()  # OK
v2: PColor = ImplicitColor()  # OK


class RGB(Protocol):
    rgb: tuple[int, int, int]
    other: int

    @abstractmethod
    def intensity(self) -> int:
        return 1

    def transparency(self) -> int:
        ...


class Point(RGB):
    def __init__(self, red: int, green: int, blue: str) -> None:
        self.rgb = red, green, blue  # E: 'blue' must be 'int'
        self.other = 0


p = Point(0, 0, "")  # E: Cannot instantiate abstract class


class Proto1(Protocol):
    cm1: ClassVar[int]
    cm2: ClassVar[int] = 0

    im1: int
    im2: int = 2
    im3: int

    def __init__(self):
        self.im3 = 3


class Proto2(Protocol):
    cm10: int


class Proto3(Proto2, Protocol):
    cm11: int


class Concrete1(Proto1):
    def __init__(self):
        self.cm1 = 1
        self.im1 = 1
        self.im3 = 3


c1 = Concrete1()  # E: cannot instantiate abstract class


class Concrete2(Proto1):
    cm1 = 3
    im1 = 0


c2 = Concrete2()


class Concrete3(Proto1, Proto3):
    cm1 = 3

    def __init__(self):
        self.im1 = 0
        self.cm10 = 10
        self.cm11 = 11


c3 = Concrete3()  # E: cannot instantiate abstract class


class Concrete4(Proto1, Proto3):
    cm1 = 3
    cm10 = 3

    def __init__(self):
        self.im1 = 3
        self.im10 = 10
        self.cm11 = 3


c4 = Concrete4()


class Proto5(Protocol):
    def method1(self) -> int:
        ...


class Concrete5(Proto5):
    pass


c5 = Concrete5()  # E: cannot instantiate abstract class


class Proto6(Protocol):
    x: int


class Mixin:
    x = 3


class Concrete6(Mixin, Proto6):
    pass


class Proto7(Protocol):
    @abstractmethod
    def method1(self):
        ...


class Mixin7(Proto7, ABC):
    def method1(self) -> None:
        pass


class Concrete7A(Proto7):
    pass


c7a = Concrete7A()  # E: cannot instantiate abstract class


class Concrete7B(Mixin7, Proto7):
    pass


c7b = Concrete7B()

"""
Tests the handling of class objects as implementations of a protocol.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/protocol.html#type-and-class-objects-vs-protocols

# > Variables and parameters annotated with Type[Proto] accept only concrete
# (non-protocol) subtypes of Proto.

from abc import abstractmethod
from typing import Any, ClassVar, Protocol


class Proto(Protocol):
    @abstractmethod
    def meth(self) -> int:
        ...


class Concrete:
    def meth(self) -> int:
        return 42


def fun(cls: type[Proto]) -> int:
    return cls().meth()  # OK


fun(Proto)  # E
fun(Concrete)  # OK


var: type[Proto]
var = Proto  # E
var = Concrete  # OK
var().meth()  # OK


# > A class object is considered an implementation of a protocol if accessing
# > all members on it results in types compatible with the protocol members.


class ProtoA1(Protocol):
    def method1(self, x: int) -> int:
        ...


class ProtoA2(Protocol):
    def method1(_self, self: Any, x: int) -> int:
        ...


class ConcreteA:
    def method1(self, x: int) -> int:
        return 0


pa1: ProtoA1 = ConcreteA  # E: signatures don't match
pa2: ProtoA2 = ConcreteA  # OK


class ProtoB1(Protocol):
    @property
    def prop1(self) -> int:
        ...


class ConcreteB:
    @property
    def prop1(self) -> int:
        return 0


pb1: ProtoB1 = ConcreteB  # E


class ProtoC1(Protocol):
    attr1: ClassVar[int]


class ProtoC2(Protocol):
    attr1: int


class ConcreteC1:
    attr1: ClassVar[int] = 1


class ConcreteC2:
    attr1: int = 1


class CMeta(type):
    attr1: int

    def __init__(self, attr1: int) -> None:
        self.attr1 = attr1


class ConcreteC3(metaclass=CMeta):
    pass


pc1: ProtoC1 = ConcreteC1  # E
pc2: ProtoC2 = ConcreteC1  # OK
pc3: ProtoC1 = ConcreteC2  # E
pc4: ProtoC2 = ConcreteC2  # E
pc5: ProtoC1 = ConcreteC3  # E
pc6: ProtoC2 = ConcreteC3  # OK

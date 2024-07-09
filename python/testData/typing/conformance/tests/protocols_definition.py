"""
Tests the basic definition rules for protocols.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/protocol.html#defining-a-protocol

from abc import abstractmethod
from typing import Any, ClassVar, Iterable, NamedTuple, Protocol, Sequence
from dataclasses import dataclass


class SupportsClose(Protocol):
    def close(self) -> None:
        ...


class Resource:
    def close(self) -> None:
        pass


def close_all(things: Iterable[SupportsClose]) -> None:
    for t in things:
        t.close()


f = open("foo.txt")
r = Resource()
close_all([f, r])  # OK
close_all([1])  # E: 'int' has no 'close' method


class Example(Protocol):
    def first(self) -> int:  # This is a protocol member
        return 42

    @abstractmethod
    def second(self) -> int:  # Method without a default implementation
        raise NotImplementedError

    # > Static methods, class methods, and properties are equally allowed in protocols.

    @staticmethod
    def third() -> int:
        ...

    @classmethod
    def fourth(cls) -> int:
        ...

    @property
    def fifth(self) -> int:
        ...


# > To define a protocol variable, one can use PEP 526 variable annotations
# > in the class body. Additional attributes only defined in the body of a
# > method by assignment via self are not allowed.


class Template(Protocol):
    name: str  # This is a protocol member
    value: int = 0  # This one too (with default)

    def method(self) -> None:
        self.name = "name"  # OK
        self.temp: list[int] = []  # E: use of self variables not allowed


class Concrete:
    def __init__(self, name: str, value: int) -> None:
        self.name = name
        self.value = value

    def method(self) -> None:
        return


var: Template = Concrete("value", 42)  # OK


# > To distinguish between protocol class variables and protocol instance
# > variables, the special ClassVar annotation should be used as specified
# > by PEP 526. By default, protocol variables as defined above are considered
# > readable and writable. To define a read-only protocol variable, one can
# > use an (abstract) property.


class Template2(Protocol):
    val1: ClassVar[Sequence[int]]


class Concrete2_Good1:
    val1: ClassVar[Sequence[int]] = [2]


class Concrete2_Bad1:
    ...


class Concrete2_Bad2:
    val1: Sequence[float] = [2]


class Concrete2_Bad3:
    val1: list[int] = [2]


class Concrete2_Bad4:
    val1: Sequence[int] = [2]


v2_good1: Template2 = Concrete2_Good1()  # OK
v2_bad1: Template2 = Concrete2_Bad1()  # E
v2_bad2: Template2 = Concrete2_Bad2()  # E
v2_bad3: Template2 = Concrete2_Bad3()  # E
v2_bad4: Template2 = Concrete2_Bad4()  # E


class Template3(Protocol):
    val1: Sequence[int]


class Concrete3_Good1:
    val1: Sequence[int] = [0]


class Concrete3_Good2:
    def __init__(self) -> None:
        self.val1: Sequence[int] = [0]


class Concrete3_Bad1:
    ...


class Concrete3_Bad2:
    val1: ClassVar[Sequence[int]] = [0]


class Concrete3_Bad3:
    @property
    def val1(self) -> Sequence[int]:
        return [0]


class Concrete3_Bad4:
    val1: Sequence[float] = [0]


class Concrete3_Bad5:
    val1: list[int] = [0]


v3_good1: Template3 = Concrete3_Good1()  # OK
v3_bad1: Template3 = Concrete3_Bad1()  # E
v3_bad2: Template3 = Concrete3_Bad2()  # E
v3_bad3: Template3 = Concrete3_Bad3()  # E
v3_bad4: Template3 = Concrete3_Bad4()  # E
v3_bad5: Template3 = Concrete3_Bad5()  # E


class Template4(Protocol):
    @property
    def val1(self) -> Sequence[float]:
        ...


class Concrete4_Good1:
    @property
    def val1(self) -> Sequence[float]:
        return [0]


class Concrete4_Good2:
    @property
    def val1(self) -> Sequence[int]:
        return [0]


class Concrete4_Good3:
    val1: Sequence[float] = [0]


class Concrete4_Good4:
    val1: Sequence[int] = [0]


class Concrete4_Good5:
    val1: list[float] = [0]


class Concrete4_Good6(NamedTuple):
    val1: Sequence[float] = [0]


@dataclass(frozen=False)
class Concrete4_Good7:
    val1: Sequence[float] = [0]


class Concrete4_Bad1:
    def val1(self) -> Sequence[int]:  # Not a property
        return [0]


class Concrete4_Bad2:
    ...


v4_good1: Template4 = Concrete4_Good1()  # OK
v4_good2: Template4 = Concrete4_Good2()  # OK
v4_good3: Template4 = Concrete4_Good3()  # OK
v4_good4: Template4 = Concrete4_Good4()  # OK
v4_good5: Template4 = Concrete4_Good5()  # OK
v4_good6: Template4 = Concrete4_Good6()  # OK
v4_good7: Template4 = Concrete4_Good7()  # OK
v4_bad1: Template4 = Concrete4_Bad1()  # E
v4_bad2: Template4 = Concrete4_Bad2()  # E


class Template5(Protocol):
    def method1(self, a: int, b: int) -> float:
        ...


class Concrete5_Good1:
    def method1(self, a: float, b: float) -> int:
        return 0


class Concrete5_Good2:
    def method1(self, *args: float, **kwargs: float) -> Any:
        return 0.0


class Concrete5_Good3:
    def method1(self, a, b) -> Any:
        return 0.0


class Concrete5_Good4:
    @classmethod
    def method1(cls, a: int, b: int) -> float:
        return 0


class Concrete5_Good5:
    @staticmethod
    def method1(a: int, b: int) -> float:
        return 0


class Concrete5_Bad1:
    def method1(self, a, c) -> int:
        return 0


class Concrete5_Bad2:
    def method1(self, a: int, c: int) -> int:
        return 0


class Concrete5_Bad3:
    def method1(self, *, a: int, b: int) -> float:
        return 0


class Concrete5_Bad4:
    def method1(self, a: int, b: int, /) -> float:
        return 0


class Concrete5_Bad5:
    @staticmethod
    def method1(self, a: int, b: int) -> float:
        return 0


v5_good1: Template5 = Concrete5_Good1()  # OK
v5_good2: Template5 = Concrete5_Good2()  # OK
v5_good3: Template5 = Concrete5_Good3()  # OK
v5_good4: Template5 = Concrete5_Good4()  # OK
v5_good5: Template5 = Concrete5_Good5()  # OK
v5_bad1: Template5 = Concrete5_Bad1()  # E
v5_bad2: Template5 = Concrete5_Bad2()  # E
v5_bad3: Template5 = Concrete5_Bad3()  # E
v5_bad4: Template5 = Concrete5_Bad4()  # E
v5_bad5: Template5 = Concrete5_Bad5()  # E


class Template6(Protocol):
    @property
    def val1(self) -> Sequence[float]:
        ...

    @val1.setter
    def val1(self, val: Sequence[float]) -> None:
        ...


class Concrete6_Good1:
    @property
    def val1(self) -> Sequence[float]:
        return [0]

    @val1.setter
    def val1(self, val: Sequence[float]) -> None:
        pass


class Concrete6_Good2:
    val1: Sequence[float] = [0]


@dataclass(frozen=False)
class Concrete6_Good3:
    val1: Sequence[float] = [0]


class Concrete6_Bad1:
    @property
    def val1(self) -> Sequence[float]:
        return [0]


class Concrete6_Bad2(NamedTuple):
    val1: Sequence[float] = [0]


@dataclass(frozen=True)
class Concrete6_Bad3:
    val1: Sequence[float] = [0]


v6_good1: Template6 = Concrete6_Good1()  # OK
v6_good2: Template6 = Concrete6_Good2()  # OK
v6_good3: Template6 = Concrete6_Good3()  # OK
v6_bad1: Template6 = Concrete6_Bad1()  # E
v6_bad2: Template6 = Concrete6_Bad2()  # E: named tuple is immutable
v6_bad3: Template6 = Concrete6_Bad3()  # E: dataclass is frozen

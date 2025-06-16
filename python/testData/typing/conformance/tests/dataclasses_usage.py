"""
Tests basic handling of the dataclass factory.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/dataclasses.html
# Also, see https://peps.python.org/pep-0557/

from dataclasses import InitVar, dataclass, field
from typing import Any, Callable, ClassVar, Generic, Protocol, TypeVar, assert_type

T = TypeVar("T")


@dataclass(order=True)
class InventoryItem:
    x = 0
    name: str
    unit_price: float
    quantity_on_hand: int = 0

    def total_cost(self) -> float:
        return self.unit_price * self.quantity_on_hand


v1 = InventoryItem("soap", 2.3)


class InventoryItemInitProto(Protocol):
    def __call__(
        self, name: str, unit_price: float, quantity_on_hand: int = ...
    ) -> None: ...


# Validate the type of the synthesized __init__ method.
x1: InventoryItemInitProto = v1.__init__

# Make sure the following additional methods were synthesized.
print(v1.__repr__)
print(v1.__eq__)
print(v1.__ne__)
print(v1.__lt__)
print(v1.__le__)
print(v1.__gt__)
print(v1.__ge__)

assert_type(v1.name, str)
assert_type(v1.unit_price, float)
assert_type(v1.quantity_on_hand, int)

v2 = InventoryItem("name")  # E: missing unit_price
v3 = InventoryItem("name", "price")  # E: incorrect type for unit_price
v4 = InventoryItem("name", 3.1, 3, 4)  # E: too many arguments


# > TypeError will be raised if a field without a default value follows a
# > field with a default value. This is true either when this occurs in a
# > single class, or as a result of class inheritance.
@dataclass  # E[DC1]
class DC1:
    a: int = 0
    b: int  # E[DC1]: field with no default cannot follow field with default.


@dataclass  # E[DC2]
class DC2:
    a: int = field(default=1)
    b: int  # E[DC2]: field with no default cannot follow field with default.


@dataclass  # E[DC3]
class DC3:
    a: InitVar[int] = 0
    b: int  # E[DC3]: field with no default cannot follow field with default.


@dataclass
class DC4:
    a: int = field(init=False)
    b: int


v5 = DC4(0)
v6 = DC4(0, 1)  # E: too many parameters


@dataclass
class DC5:
    a: int = field(default_factory=str)  # E: type mismatch


def f(s: str) -> int:
    return int(s)


@dataclass
class DC6:
    a: ClassVar[int] = 0
    b: str
    c: Callable[[str], int] = f


dc6 = DC6("")
assert_type(dc6.a, int)
assert_type(DC6.a, int)
assert_type(dc6.b, str)
assert_type(dc6.c, Callable[[str], int])


@dataclass
class DC7:
    x: int


@dataclass(init=False)
class DC8(DC7):
    y: int

    def __init__(self, a: DC7, y: int):
        self.__dict__ = a.__dict__
        self.y = y


a = DC7(3)
b = DC8(a, 5)

# This should generate an error because there is an extra parameter
DC7(3, 4)  # E

# This should generate an error because there is one too few parameters
DC8(a)  # E


@dataclass
class DC9:
    a: str = field(init=False, default="s")
    b: bool = field()


@dataclass
class DC10(DC9):
    a: str = field()
    b: bool = field()


@dataclass(init=False)
class DC11:
    x: int
    x_squared: int

    def __init__(self, x: int):
        self.x = x
        self.x_squared = x * x


DC11(3)


@dataclass(init=True)
class DC12:
    x: int
    x_squared: int

    def __init__(self, x: int):
        self.x = x
        self.x_squared = x * x


DC12(3)


@dataclass(init=False)
class DC13:
    x: int
    x_squared: int


# This should generate an error because there is no matching
# override __init__ method and no synthesized __init__.
DC13(3)  # E


@dataclass
class DC14:
    prop_1: str = field(init=False)
    prop_2: str = field(default="hello")
    prop_3: str = field(default_factory=lambda: "hello")


@dataclass
class DC15(DC14):
    prop_2: str = ""


dc15 = DC15(prop_2="test")

assert_type(dc15.prop_1, str)
assert_type(dc15.prop_2, str)
assert_type(dc15.prop_3, str)


class DataclassProto(Protocol):
    __dataclass_fields__: ClassVar[dict[str, Any]]


v7: DataclassProto = dc15


@dataclass
class DC16(Generic[T]):
    value: T


assert_type(DC16(1), DC16[int])


class DC17(DC16[str]):
    pass


assert_type(DC17(""), DC17)


@dataclass
class DC18:
    x: int = field()
    # This may generate a type checker error because an unannotated field
    # will result in a runtime exception.
    y = field()  # E?

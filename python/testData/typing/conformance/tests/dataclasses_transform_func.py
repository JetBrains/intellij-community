"""
Tests the dataclass_transform mechanism when it is applied to a decorator function.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/dataclasses.html#the-dataclass-transform-decorator

from typing import Any, Callable, TypeVar, dataclass_transform, overload

T = TypeVar("T")


@overload
@dataclass_transform(kw_only_default=True, order_default=True)
def create_model(cls: T) -> T:
    ...


@overload
@dataclass_transform(kw_only_default=True, order_default=True)
def create_model(
    *,
    frozen: bool = False,
    kw_only: bool = True,
    order: bool = True,
) -> Callable[[T], T]:
    ...


def create_model(*args: Any, **kwargs: Any) -> Any:
    ...


@create_model(kw_only=False, order=False)
class Customer1:
    id: int
    name: str


@create_model(frozen=True)
class Customer2:
    id: int
    name: str


@create_model(frozen=True)
class Customer2Subclass(Customer2):
    salary: float


c1_1 = Customer1(id=3, name="Sue")
c1_1.id = 4

c1_2 = Customer1(3, "Sue")
c1_2.name = "Susan"

# This should generate an error because of a type mismatch.
c1_2.name = 3  # E

# This should generate an error because comparison methods are
# not synthesized.
v1 = c1_1 < c1_2   # E

# This should generate an error because salary is not
# a defined field.
c1_3 = Customer1(id=3, name="Sue", salary=40000)  # E

c2_1 = Customer2(id=0, name="John")

# This should generate an error because Customer2 supports
# keyword-only parameters for its constructor.
c2_2 = Customer2(0, "John")  # E

v2 = c2_1 < c2_2


@dataclass_transform(kw_only_default=True, order_default=True, frozen_default=True)
def create_model_frozen(cls: T) -> T:
    ...


@create_model_frozen
class Customer3:
    id: int
    name: str


# This should generate an error because a non-frozen class
# cannot inherit from a frozen class.
@create_model  # E[Customer3Subclass]
class Customer3Subclass(Customer3):  # E[Customer3Subclass]
    age: int


c3_1 = Customer3(id=2, name="hi")

# This should generate an error because Customer3 is frozen.
c3_1.id = 4  # E

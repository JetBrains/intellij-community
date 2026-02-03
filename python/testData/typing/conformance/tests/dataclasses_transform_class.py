"""
Tests the dataclass_transform mechanism when it is applied to a base class.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/dataclasses.html#the-dataclass-transform-decorator

from typing import Any, Generic, TypeVar, dataclass_transform

T = TypeVar("T")


class ModelField:
    def __init__(self, *, init: bool = True, default: Any | None = None) -> None:
        ...


def model_field(
    *, init: bool = True, default: Any | None = None, alias: str | None = None
) -> Any:
    ...


@dataclass_transform(
    kw_only_default=True,
    field_specifiers=(ModelField, model_field),
)
class ModelBase:
    not_a_field: str

    def __init_subclass__(
        cls,
        *,
        frozen: bool = False,
        kw_only: bool = True,
        order: bool = True,
    ) -> None:
        ...

    def __init__(self, not_a_field: str) -> None:
        self.not_a_field = not_a_field


class Customer1(ModelBase, frozen=True):
    id: int = model_field()
    name: str = model_field()
    name2: str = model_field(alias="other_name", default="None")


# This should generate an error because a non-frozen dataclass cannot
# derive from a frozen one.
class Customer1Subclass(Customer1):  # E
    salary: float = model_field()


class Customer2(ModelBase, order=True):
    id: int
    name: str = model_field(default="None")


c1_1 = Customer1(id=3, name="Sue", other_name="Susan")

# This should generate an error because the class is frozen.
c1_1.id = 4  # E

# This should generate an error because the class is kw_only.
c1_2 = Customer1(3, "Sue")  # E

c1_3 = Customer1(id=3, name="John")

# This should generate an error because comparison methods are
# not synthesized.
v1 = c1_1 < c1_2  # E

c2_1 = Customer2(id=0, name="John")

c2_2 = Customer2(id=1)

v2 = c2_1 < c2_2

# This should generate an error because Customer2 supports
# keyword-only parameters for its constructor.
c2_3 = Customer2(0, "John")  # E


@dataclass_transform(
    kw_only_default=True,
    field_specifiers=(ModelField, model_field),
)
class GenericModelBase(Generic[T]):
    not_a_field: T

    def __init_subclass__(
        cls,
        *,
        frozen: bool = False,
        kw_only: bool = True,
        order: bool = True,
    ) -> None:
        ...


class GenericCustomer(GenericModelBase[int]):
    id: int = model_field()


gc_1 = GenericCustomer(id=3)


@dataclass_transform(frozen_default=True)
class ModelBaseFrozen:
    not_a_field: str


class Customer3(ModelBaseFrozen):
    id: int
    name: str


c3_1 = Customer3(id=2, name="hi")

# This should generate an error because Customer3 is frozen.
c3_1.id = 4  # E

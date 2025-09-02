"""
Tests the dataclass_transform mechanism supports the "converter" parameter
in a field specifier class.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/dataclasses.html#converters

from typing import (
    Any,
    Callable,
    TypeVar,
    dataclass_transform,
    overload,
)

T = TypeVar("T")
S = TypeVar("S")


def model_field(
    *,
    converter: Callable[[S], T],
    default: S | None = None,
    default_factory: Callable[[], S] | None = None,
) -> T:
    ...


@dataclass_transform(field_specifiers=(model_field,))
class ModelBase:
    ...


# > The converter must be a callable that must accept a single positional
# > argument (but may accept other optional arguments, which are ignored for
# > typing purposes).


def bad_converter1() -> int:
    return 0


def bad_converter2(*, x: int) -> int:
    return 0


class DC1(ModelBase):
    field1: int = model_field(converter=bad_converter1)  # E
    field2: int = model_field(converter=bad_converter2)  # E


# > The type of the first positional parameter provides the type of the
# > synthesized __init__ parameter for the field.

# > The return type of the callable must be assignable to the field’s
# > declared type.


def converter_simple(s: str) -> int:
    return int(s)


def converter_with_param_before_args(s: str, *args: int, **kwargs: int) -> int:
    return int(s)


def converter_with_args(*args: str) -> int:
    return int(args[0])


@overload
def overloaded_converter(s: str) -> int:
    ...


@overload
def overloaded_converter(s: list[str]) -> int:
    ...


def overloaded_converter(s: str | list[str], *args: str) -> int | str:
    return 0


class ConverterClass:
    @overload
    def __init__(self, val: str) -> None:
        ...

    @overload
    def __init__(self, val: bytes) -> None:
        ...

    def __init__(self, val: str | bytes) -> None:
        pass


class DC2(ModelBase):
    field0: int = model_field(converter=converter_simple)
    field1: int = model_field(converter=converter_with_param_before_args)
    field2: int = model_field(converter=converter_with_args)
    field3: ConverterClass = model_field(converter=ConverterClass)
    field4: int = model_field(converter=overloaded_converter)
    field5: dict[str, str] = model_field(converter=dict, default=())


DC2(1, "f1", "f2", b"f3", [])  # E
DC2("f0", "f1", "f2", 1, [])  # E
DC2("f0", "f1", "f2", "f3", 3j)  # E


dc1 = DC2("f0", "f1", "f2", b"f6", [])

dc1.field0 = "f1"
dc1.field3 = "f6"
dc1.field3 = b"f6"

dc1.field0 = 1  # E
dc1.field3 = 1  # E

dc2 = DC2("f0", "f1", "f2", "f6", "1", (("a", "1"), ("b", "2")))


# > If default or default_factory are provided, the type of the default value
# > should be assignable to the first positional parameter of the converter.


class DC3(ModelBase):
    field0: int = model_field(converter=converter_simple, default="")
    field1: int = model_field(converter=converter_simple, default=1)  # E

    field2: int = model_field(converter=converter_simple, default_factory=str)
    field3: int = model_field(converter=converter_simple, default_factory=int)  # E

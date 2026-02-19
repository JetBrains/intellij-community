"""
Tests handling of callback protocols.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/callables.html#callback-protocols

from typing import Any, Callable, ParamSpec, Protocol, TypeVar, cast, overload

InputT = TypeVar("InputT", contravariant=True)
OutputT = TypeVar("OutputT", covariant=True)


class Proto1(Protocol):
    def __call__(self, *vals: bytes, max_len: int | None = None) -> list[bytes]:
        ...


def cb1_good1(*vals: bytes, max_len: int | None = None) -> list[bytes]:
    return []


def cb1_bad1(*vals: bytes, max_items: int | None) -> list[bytes]:
    return []


def cb1_bad2(*vals: bytes) -> list[bytes]:
    return []


def cb1_bad3(*vals: bytes, max_len: str | None) -> list[bytes]:
    return []


cb1: Proto1 = cb1_good1  # OK
cb1 = cb1_bad1  # E: different names
cb1 = cb1_bad2  # E: parameter types
cb1 = cb1_bad3  # E: default argument


class Proto2(Protocol):
    def __call__(self, *vals: bytes, **kwargs: str) -> None:
        pass


def cb2_good1(*a: bytes, **b: str):
    pass


def cb2_bad1(*a: bytes):
    pass


def cb2_bad2(*a: str, **b: str):
    pass


def cb2_bad3(*a: bytes, **b: bytes):
    pass


def cb2_bad4(**b: str):
    pass


cb2: Proto2 = cb2_good1  # OK

cb2 = cb2_bad1  # E: missing **kwargs
cb2 = cb2_bad2  # E: parameter type
cb2 = cb2_bad3  # E: parameter type
cb2 = cb2_bad4  # E: missing parameter


class Proto3(Protocol):
    def __call__(self) -> None:
        pass


cb3: Proto3 = cb2_good1  # OK
cb3 = cb2_bad1  # OK
cb3 = cb2_bad2  # OK
cb3 = cb2_bad3  # OK
cb3 = cb2_bad4  # OK


# A callback protocol with other attributes.
class Proto4(Protocol):
    other_attribute: int

    def __call__(self, x: int) -> None:
        pass


def cb4_bad1(x: int) -> None:
    pass


var4: Proto4 = cb4_bad1  # E: missing attribute


class Proto5(Protocol):
    def __call__(self, *, a: int, b: str) -> int:
        ...


def cb5_good1(a: int, b: str) -> int:
    return 0


cb5: Proto5 = cb5_good1  # OK


class NotProto6:
    def __call__(self, *vals: bytes, maxlen: int | None = None) -> list[bytes]:
        return []


def cb6_bad1(*vals: bytes, max_len: int | None = None) -> list[bytes]:
    return []


cb6: NotProto6 = cb6_bad1  # E: NotProto6 isn't a protocol class


class Proto7(Protocol[InputT, OutputT]):
    def __call__(self, inputs: InputT) -> OutputT:
        ...


class Class7_1:
    # Test for unannotated parameter.
    def __call__(self, inputs) -> int:
        return 5


cb7_1: Proto7[int, int] = Class7_1()  # OK


class Class7_2:
    # Test for parameter with type Any.
    def __call__(self, inputs: Any) -> int:
        return 5


cb7_2: Proto7[int, int] = Class7_2()  # OK


class Proto8(Protocol):
    @overload
    def __call__(self, x: int) -> int:
        ...

    @overload
    def __call__(self, x: str) -> str:
        ...

    def __call__(self, x: Any) -> Any:
        ...


def cb8_good1(x: Any) -> Any:
    return x


def cb8_bad1(x: int) -> Any:
    return x


cb8: Proto8 = cb8_good1  # OK
cb8 = cb8_bad1  # E: parameter type


P = ParamSpec("P")
R = TypeVar("R", covariant=True)


class Proto9(Protocol[P, R]):
    other_attribute: int

    def __call__(self, *args: P.args, **kwargs: P.kwargs) -> R:
        ...


def decorator1(f: Callable[P, R]) -> Proto9[P, R]:
    converted = cast(Proto9[P, R], f)
    converted.other_attribute = 1
    converted.other_attribute = "str"  # E: incompatible type
    converted.xxx = 3  # E: unknown attribute
    return converted


@decorator1
def cb9_good(x: int) -> str:
    return ""


print(cb9_good.other_attribute)  # OK
print(cb9_good.other_attribute2)  # E: unknown attribute

cb9_good(x=3)


class Proto10(Protocol):
    __name__: str
    __module__: str
    __qualname__: str
    __annotations__: dict[str, Any]

    def __call__(self) -> None:
        ...


def cb10_good() -> None:
    pass


cb10: Proto10 = cb10_good  # OK


class Proto11(Protocol):
    def __call__(self, x: int, /, y: str) -> Any:
        ...


def cb11_good1(x: int, /, y: str, z: None = None) -> Any:
    pass


def cb11_good2(x: int, y: str, z: None = None) -> Any:
    pass


def cb11_bad1(x: int, y: str, /) -> Any:
    pass


cb11: Proto11 = cb11_good1  # OK
cb11 = cb11_good2  # OK
cb11 = cb11_bad1  # E: y is position-only


class Proto12(Protocol):
    def __call__(self, *args: Any, kwarg0: Any, kwarg1: Any) -> None:
        ...


def cb12_good1(*args: Any, kwarg0: Any, kwarg1: Any) -> None:
    pass


def cb12_good2(*args: Any, **kwargs: Any) -> None:
    pass


def cb12_bad1(*args: Any, kwarg0: Any) -> None:
    pass


cb12: Proto12 = cb12_good1  # OK
cb12 = cb12_good2  # OK
cb12 = cb12_bad1  # E: missing kwarg1


class Proto13_Default(Protocol):
    # Callback with positional parameter with default arg value
    def __call__(self, path: str = ...) -> str:
        ...


class Proto13_NoDefault(Protocol):
    # Callback with positional parameter but no default arg value
    def __call__(self, path: str) -> str:
        ...


def cb13_default(path: str = "") -> str:
    return ""


def cb13_no_default(path: str) -> str:
    return ""


cb13_1: Proto13_Default = cb13_default  # OK
cb13_2: Proto13_Default = cb13_no_default  # E: no default

cb13_3: Proto13_NoDefault = cb13_default  # OK
cb13_4: Proto13_NoDefault = cb13_no_default  # OK


class Proto14_Default(Protocol):
    # Callback with keyword parameter with default arg value
    def __call__(self, *, path: str = ...) -> str:
        ...


class Proto14_NoDefault(Protocol):
    # Callback with keyword parameter with no default arg value
    def __call__(self, *, path: str) -> str:
        ...


def cb14_default(*, path: str = "") -> str:
    return ""


def cb14_no_default(*, path: str) -> str:
    return ""


cb14_1: Proto14_Default = cb14_default  # OK
cb14_2: Proto14_Default = cb14_no_default  # E: no default
cb14_3: Proto14_NoDefault = cb14_default  # OK
cb14_4: Proto14_NoDefault = cb14_no_default  # OK

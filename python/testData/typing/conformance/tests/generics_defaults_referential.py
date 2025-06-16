"""
Tests for type parameter default values that reference other type parameters.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#defaults-for-type-parameters.

# > To use another type parameter as a default the ``default`` and the
# > type parameter must be the same type (a ``TypeVar``'s default must be
# > a ``TypeVar``, etc.).

from typing import Any, Generic, assert_type
from typing_extensions import TypeVar

DefaultStrT = TypeVar("DefaultStrT", default=str)
StartT = TypeVar("StartT", default=int)
StopT = TypeVar("StopT", default=StartT)
StepT = TypeVar("StepT", default=int | None)


class slice(Generic[StartT, StopT, StepT]): ...


assert_type(slice, type[slice[int, int, int | None]])
assert_type(slice(), slice[int, int, int | None])
assert_type(slice[str](), slice[str, str, int | None])
assert_type(slice[str, bool, complex](), slice[str, bool, complex])

T2 = TypeVar("T2", default=DefaultStrT)


class Foo(Generic[DefaultStrT, T2]):
    def __init__(self, a: DefaultStrT, b: T2) -> None: ...


assert_type(Foo(1, ""), Foo[int, str])
Foo[int](1, "")  # E: Foo[int, str] cannot be assigned to self: Foo[int, int] in Foo.__init__
Foo[int]("", 1)  # E: Foo[str, int] cannot be assigned to self: Foo[int, int] in Foo.__init__


# > ``T1`` must be used before ``T2`` in the parameter list of the generic.

S1 = TypeVar("S1")
S2 = TypeVar("S2", default=S1)


class Foo2(Generic[S1, S2]): ...  # OK


Start2T = TypeVar("Start2T", default="StopT")
Stop2T = TypeVar("Stop2T", default=int)


class slice2(Generic[Start2T, Stop2T, StepT]): ...  # E: bad ordering


# > Using a type parameter from an outer scope as a default is not supported.


class Foo3(Generic[S1]):
    class Bar2(Generic[S2]): ...  # E


# > ``T1``'s bound must be a subtype of ``T2``'s bound.

X1 = TypeVar("X1", bound=int)
Ok1 = TypeVar("Ok1", default=X1, bound=float)  # OK
AlsoOk1 = TypeVar("AlsoOk1", default=X1, bound=int)  # OK
Invalid1 = TypeVar("Invalid1", default=X1, bound=str)  # E: int is not a subtype of str


# > The constraints of ``T2`` must be a superset of the constraints of ``T1``.

Y1 = TypeVar("Y1", bound=int)
Invalid2 = TypeVar("Invalid2", float, str, default=Y1)  # E: upper bound int is incompatible with constraints float or str

Y2 = TypeVar("Y2", int, str)
AlsoOk2 = TypeVar("AlsoOk2", int, str, bool, default=Y2)  # OK
AlsoInvalid2 = TypeVar("AlsoInvalid2", bool, complex, default=Y2)  # E: {bool, complex} is not a superset of {int, str}


# > Type parameters are valid as parameters to generics inside of a
# > ``default`` when the first parameter is in scope as determined by the
# > `previous section <scoping rules_>`_.


Z1 = TypeVar("Z1")
ListDefaultT = TypeVar("ListDefaultT", default=list[Z1])  # OK


class Bar(Generic[Z1, ListDefaultT]):  # OK
    def __init__(self, x: Z1, y: ListDefaultT): ...


assert_type(Bar, type[Bar[Any, list[Any]]])
assert_type(Bar[int], type[Bar[int, list[int]]])
assert_type(Bar[int](0, []), Bar[int, list[int]])
assert_type(Bar[int, list[str]](0, []), Bar[int, list[str]])
assert_type(Bar[int, str](0, ""), Bar[int, str])

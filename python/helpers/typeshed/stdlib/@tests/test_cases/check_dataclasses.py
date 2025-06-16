from __future__ import annotations

import dataclasses as dc
import sys
from typing import TYPE_CHECKING, Any, Dict, FrozenSet, Tuple, Type, Union
from typing_extensions import Annotated, assert_type

if TYPE_CHECKING:
    from _typeshed import DataclassInstance


@dc.dataclass
class Foo:
    attr: str


assert_type(dc.fields(Foo), Tuple[dc.Field[Any], ...])

dc.asdict(Foo)  # type: ignore
dc.astuple(Foo)  # type: ignore
dc.replace(Foo)  # type: ignore

# See #9723 for why we can't make this assertion
# if dc.is_dataclass(Foo):
#     assert_type(Foo, Type[Foo])

f = Foo(attr="attr")

assert_type(dc.fields(f), Tuple[dc.Field[Any], ...])
assert_type(dc.asdict(f), Dict[str, Any])
assert_type(dc.astuple(f), Tuple[Any, ...])
assert_type(dc.replace(f, attr="new"), Foo)

if dc.is_dataclass(f):
    # The inferred type doesn't change
    # if it's already known to be a subtype of _DataclassInstance
    assert_type(f, Foo)


def is_dataclass_any(arg: Any) -> None:
    if dc.is_dataclass(arg):
        assert_type(arg, Union["DataclassInstance", Type["DataclassInstance"]])


def is_dataclass_object(arg: object) -> None:
    if dc.is_dataclass(arg):
        assert_type(arg, Union["DataclassInstance", Type["DataclassInstance"]])


def is_dataclass_type(arg: type) -> None:
    if dc.is_dataclass(arg):
        assert_type(arg, Type["DataclassInstance"])


def check_other_isdataclass_overloads(x: type, y: object) -> None:
    # TODO: neither pyright nor mypy emit error on this -- why?
    # dc.fields(x)

    dc.fields(y)  # type: ignore

    dc.asdict(x)  # type: ignore
    dc.asdict(y)  # type: ignore

    dc.astuple(x)  # type: ignore
    dc.astuple(y)  # type: ignore

    dc.replace(x)  # type: ignore
    dc.replace(y)  # type: ignore

    if dc.is_dataclass(x):
        assert_type(x, Type["DataclassInstance"])
        assert_type(dc.fields(x), Tuple[dc.Field[Any], ...])

        dc.asdict(x)  # type: ignore
        dc.astuple(x)  # type: ignore
        dc.replace(x)  # type: ignore

    if dc.is_dataclass(y):
        assert_type(y, Union["DataclassInstance", Type["DataclassInstance"]])
        assert_type(dc.fields(y), Tuple[dc.Field[Any], ...])

        dc.asdict(y)  # type: ignore
        dc.astuple(y)  # type: ignore
        dc.replace(y)  # type: ignore

    if dc.is_dataclass(y) and not isinstance(y, type):
        assert_type(y, "DataclassInstance")
        assert_type(dc.fields(y), Tuple[dc.Field[Any], ...])
        assert_type(dc.asdict(y), Dict[str, Any])
        assert_type(dc.astuple(y), Tuple[Any, ...])
        dc.replace(y)


class _D: ...


custom_dc = dc.dataclass(_D, init=True)
assert_type(custom_dc, type[_D])

custom_dc_2 = dc.dataclass(None, init=True)(_D)
assert_type(custom_dc_2, type[_D])


# Regression test for #11653
D = dc.make_dataclass(
    "D", [("a", Union[int, None]), "y", ("z", Annotated[FrozenSet[bytes], "metadata"], dc.field(default=frozenset({b"foo"})))]
)
# Check that it's inferred by the type checker as a class object of some kind
# (but don't assert the exact type that `D` is inferred as,
# in case a type checker decides to add some special-casing for
# `make_dataclass` in the future)
assert_type(D.__mro__, Tuple[type, ...])


if sys.version_info >= (3, 14):
    from typing import TypeVar

    _T = TypeVar("_T")

    def custom_dataclass(
        cls: type[_T],
        /,
        *,
        init: bool = True,
        repr: bool = True,
        eq: bool = True,
        order: bool = False,
        unsafe_hash: bool = False,
        frozen: bool = False,
        match_args: bool = True,
        kw_only: bool = False,
        slots: bool = False,
        weakref_slot: bool = False,
    ) -> type[_T]:
        custom_dc_maker = dc.dataclass(
            init=init,
            repr=repr,
            eq=eq,
            order=order,
            unsafe_hash=unsafe_hash,
            frozen=frozen,
            match_args=match_args,
            kw_only=kw_only,
            slots=slots,
            weakref_slot=weakref_slot,
        )
        return custom_dc_maker(cls)

    dc.make_dataclass(
        "D",
        [("a", Union[int, None]), "y", ("z", Annotated[FrozenSet[bytes], "metadata"], dc.field(default=frozenset({b"foo"})))],
        decorator=custom_dataclass,
    )

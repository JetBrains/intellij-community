from __future__ import annotations

import os
from typing import Any, Dict, Generic, Iterable, Mapping, TypeVar, Union
from typing_extensions import Self, assert_type

###################################################################
# Note: tests for `dict.update()` are in `check_MutableMapping.py`.
###################################################################

# These do follow `__init__` overloads order:
# mypy and pyright have different opinions about this one:
# mypy raises: 'Need type annotation for "bad"'
# pyright is fine with it.
# https://github.com/python/mypy/issues/12358
# bad = dict()
good: dict[str, str] = dict()
assert_type(good, Dict[str, str])

assert_type(dict(arg=1), Dict[str, int])

_KT = TypeVar("_KT")
_VT = TypeVar("_VT")


class KeysAndGetItem(Generic[_KT, _VT]):
    data: dict[_KT, _VT]

    def __init__(self, data: dict[_KT, _VT]) -> None:
        self.data = data

    def keys(self) -> Iterable[_KT]:
        return self.data.keys()

    def __getitem__(self, __k: _KT) -> _VT:
        return self.data[__k]


kt1: KeysAndGetItem[int, str] = KeysAndGetItem({0: ""})
assert_type(dict(kt1), Dict[int, str])
dict(kt1, arg="a")  # type: ignore

kt2: KeysAndGetItem[str, int] = KeysAndGetItem({"": 0})
assert_type(dict(kt2, arg=1), Dict[str, int])


def test_iterable_tuple_overload(x: Iterable[tuple[int, str]]) -> dict[int, str]:
    return dict(x)


i1: Iterable[tuple[int, str]] = [(1, "a"), (2, "b")]
test_iterable_tuple_overload(i1)
dict(i1, arg="a")  # type: ignore

i2: Iterable[tuple[str, int]] = [("a", 1), ("b", 2)]
assert_type(dict(i2, arg=1), Dict[str, int])

i3: Iterable[str] = ["a.b"]
i4: Iterable[bytes] = [b"a.b"]
assert_type(dict(string.split(".") for string in i3), Dict[str, str])
assert_type(dict(string.split(b".") for string in i4), Dict[bytes, bytes])

dict(["foo", "bar", "baz"])  # type: ignore
dict([b"foo", b"bar", b"baz"])  # type: ignore

# Exploring corner cases of dict.get()
d_any: dict[str, Any] = {}
d_str: dict[str, str] = {}
any_value: Any = None
str_value = "value"
int_value = 1

assert_type(d_any["key"], Any)
assert_type(d_any.get("key"), Union[Any, None])
assert_type(d_any.get("key", None), Union[Any, None])
assert_type(d_any.get("key", any_value), Any)
assert_type(d_any.get("key", str_value), Any)
assert_type(d_any.get("key", int_value), Any)

assert_type(d_str["key"], str)
assert_type(d_str.get("key"), Union[str, None])
assert_type(d_str.get("key", None), Union[str, None])
# Pyright has str instead of Any here
assert_type(d_str.get("key", any_value), Any)  # pyright: ignore[reportAssertTypeFailure]
assert_type(d_str.get("key", str_value), str)
assert_type(d_str.get("key", int_value), Union[str, int])

# Now with context!
result: str
result = d_any["key"]
result = d_any.get("key")  # type: ignore[assignment]
result = d_any.get("key", None)  # type: ignore[assignment]
result = d_any.get("key", any_value)
result = d_any.get("key", str_value)
result = d_any.get("key", int_value)

result = d_str["key"]
result = d_str.get("key")  # type: ignore[assignment]
result = d_str.get("key", None)  # type: ignore[assignment]
# Pyright has str | None here, see https://github.com/microsoft/pyright/discussions/9570
result = d_str.get("key", any_value)  # pyright: ignore[reportAssignmentType]
result = d_str.get("key", str_value)
result = d_str.get("key", int_value)  # type: ignore[arg-type]


# Return values also make things weird

# Pyright doesn't have a version of no-any-return,
# and mypy doesn't have a type: ignore that pyright will ignore.
# def test1() -> str:
#     return d_any["key"]  # mypy: ignore[no-any-return]


def test2() -> str:
    return d_any.get("key")  # type: ignore[return-value]


# def test3() -> str:
#     return d_any.get("key", None)  # mypy: ignore[no-any-return]
#
#
# def test4() -> str:
#     return d_any.get("key", any_value)  # mypy: ignore[no-any-return]
#
#
# def test5() -> str:
#     return d_any.get("key", str_value)  # mypy: ignore[no-any-return]
#
#
# def test6() -> str:
#     return d_any.get("key", int_value)  # mypy: ignore[no-any-return]


def test7() -> str:
    return d_str["key"]


def test8() -> str:
    return d_str.get("key")  # type: ignore[return-value]


def test9() -> str:
    return d_str.get("key", None)  # type: ignore[return-value]


def test10() -> str:
    return d_str.get("key", any_value)  # type: ignore[no-any-return]


def test11() -> str:
    return d_str.get("key", str_value)


def test12() -> str:
    return d_str.get("key", int_value)  # type: ignore[arg-type]


# Tests for `dict.__(r)or__`.


class CustomDictSubclass(dict[_KT, _VT]):
    pass


class CustomMappingWithDunderOr(Mapping[_KT, _VT]):
    def __or__(self, other: Mapping[_KT, _VT]) -> dict[_KT, _VT]:
        return {}

    def __ror__(self, other: Mapping[_KT, _VT]) -> dict[_KT, _VT]:
        return {}

    def __ior__(self, other: Mapping[_KT, _VT]) -> Self:
        return self


def test_dict_dot_or(
    a: dict[int, int],
    b: CustomDictSubclass[int, int],
    c: dict[str, str],
    d: Mapping[int, int],
    e: CustomMappingWithDunderOr[str, str],
) -> None:
    # dict.__(r)or__ always returns a dict, even if called on a subclass of dict:
    assert_type(a | b, dict[int, int])
    assert_type(b | a, dict[int, int])

    assert_type(a | c, dict[Union[int, str], Union[int, str]])

    # arbitrary mappings are not accepted by `dict.__or__`;
    # it has to be a subclass of `dict`
    a | d  # type: ignore

    # but Mappings such as `os._Environ` or `CustomMappingWithDunderOr`,
    # which define `__ror__` methods that accept `dict`, are fine:
    assert_type(a | os.environ, dict[Union[str, int], Union[str, int]])
    assert_type(os.environ | a, dict[Union[str, int], Union[str, int]])

    assert_type(c | os.environ, dict[str, str])
    assert_type(c | e, dict[str, str])

    assert_type(os.environ | c, dict[str, str])
    assert_type(e | c, dict[str, str])

    # store "untainted" `CustomMappingWithDunderOr[str, str]` to test `__ior__` against ` dict[str, str]` later
    # Invalid `e |= a` causes pyright to join `Unknown` to `e`'s type
    f = e

    e |= c
    e |= a  # type: ignore

    c |= f

    c |= a  # type: ignore

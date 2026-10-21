from __future__ import annotations

from collections.abc import Iterator
from typing import Generic, TypeVar
from typing_extensions import assert_type

x: list[int] = []
assert_type(list(reversed(x)), "list[int]")


class MyReversible:
    def __iter__(self) -> Iterator[str]:
        yield "blah"

    def __reversed__(self) -> Iterator[str]:
        yield "blah"


assert_type(list(reversed(MyReversible())), "list[str]")


_T = TypeVar("_T")


class MyLenAndGetItem(Generic[_T]):
    def __len__(self) -> int:
        return 0

    def __getitem__(self, item: int) -> _T:
        raise KeyError


len_and_get_item: MyLenAndGetItem[int] = MyLenAndGetItem()
assert_type(reversed(len_and_get_item), "reversed[int]")


class UnTrue:
    def __reversed__(self) -> UnFalse:
        return UnFalse()


class UnFalse:
    def __reversed__(self) -> UnTrue:
        return UnTrue()


assert_type(reversed(UnTrue()), "UnFalse")
assert_type(reversed(reversed(UnTrue())), "UnTrue")

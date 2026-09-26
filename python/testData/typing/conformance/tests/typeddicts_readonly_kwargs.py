"""
Tests the use of unpacked TypedDicts with read-only items when used to annotate
a **kwargs parameter.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/typeddict.html#read-only-items

from typing import Protocol, TypedDict, Unpack
from typing_extensions import ReadOnly

# > :pep:`692` introduced ``Unpack`` to annotate ``**kwargs`` with a
# > ``TypedDict``. Marking one or more of the items of a ``TypedDict`` used
# > in this way as read-only will have no effect on the type signature of
# > the method. However, it *will* prevent the item from being modified in
# > the body of the function.


class Args(TypedDict):
    key1: int
    key2: str


class ReadOnlyArgs(TypedDict):
    key1: ReadOnly[int]
    key2: ReadOnly[str]


class Function(Protocol):
    def __call__(self, **kwargs: Unpack[Args]) -> None: ...


def impl(**kwargs: Unpack[ReadOnlyArgs]) -> None:
    kwargs["key1"] = 3  # E


fn: Function = impl  # OK

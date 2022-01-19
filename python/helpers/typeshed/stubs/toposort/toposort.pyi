from _typeshed import SupportsItems
from typing import Any, Iterable, Iterator, Protocol, TypeVar

_KT_co = TypeVar("_KT_co", covariant=True)
_VT_co = TypeVar("_VT_co", covariant=True)
_T = TypeVar("_T")

class _SupportsItemsAndLen(SupportsItems[_KT_co, _VT_co], Protocol[_KT_co, _VT_co]):
    def __len__(self) -> int: ...

class CircularDependencyError(ValueError):
    data: dict[Any, set[Any]]
    def __init__(self, data: dict[Any, set[Any]]) -> None: ...

def toposort(data: _SupportsItemsAndLen[_T, Iterable[_T]]) -> Iterator[set[_T]]: ...
def toposort_flatten(data: _SupportsItemsAndLen[_T, Iterable[_T]], sort: bool = ...) -> list[_T]: ...

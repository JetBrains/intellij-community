from _typeshed import SupportsKeysAndGetItem
from collections.abc import Iterable
from typing import Generic, TypeVar, overload

_KT = TypeVar("_KT")
_KT2 = TypeVar("_KT2")
_VT = TypeVar("_VT")
_VT2 = TypeVar("_VT2")

class immutabledict(dict[_KT, _VT], Generic[_KT, _VT]):
    @overload
    def union(self, __dict: dict[_KT2, _VT2]) -> immutabledict[_KT | _KT2, _VT | _VT2]: ...
    @overload
    def union(self, __dict: None = ..., **kw: SupportsKeysAndGetItem[_KT2, _VT2]) -> immutabledict[_KT | _KT2, _VT | _VT2]: ...
    def merge_with(
        self, *args: SupportsKeysAndGetItem[_KT | _KT2, _VT2] | Iterable[tuple[_KT2, _VT2]] | None
    ) -> immutabledict[_KT | _KT2, _VT | _VT2]: ...

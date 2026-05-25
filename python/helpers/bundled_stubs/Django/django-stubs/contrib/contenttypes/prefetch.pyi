from collections.abc import Sequence
from typing import Any, Generic

from django.db.models import Model, Prefetch
from django.db.models.query import QuerySet
from typing_extensions import TypeVar, override

# The type of the lookup passed to Prefetch(...)
# This will be specialized to a `LiteralString` in the plugin for further processing and validation
_LookupT = TypeVar("_LookupT", bound=str, covariant=True)
# The type of the querysets passed to GenericPrefetch(...)
_PrefetchedQuerySetsT = TypeVar(
    "_PrefetchedQuerySetsT", bound=Sequence[QuerySet[Model]], covariant=True, default=list[QuerySet[Model]]
)
# The attribute name to store the prefetched list[_PrefetchedQuerySetT]
# This will be specialized to a `LiteralString` in the plugin for further processing and validation
_ToAttrT = TypeVar("_ToAttrT", bound=str, covariant=True, default=str)

class GenericPrefetch(Prefetch, Generic[_LookupT, _PrefetchedQuerySetsT, _ToAttrT]):
    def __init__(self, lookup: _LookupT, querysets: _PrefetchedQuerySetsT, to_attr: _ToAttrT | None = None) -> None: ...
    @override
    def __getstate__(self) -> dict[str, Any]: ...
    @override
    def get_current_querysets(self, level: int) -> _PrefetchedQuerySetsT | None: ...

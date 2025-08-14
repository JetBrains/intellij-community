from collections.abc import Iterable, Mapping
from typing import Any

from django.db.models.fields import Field
from django.db.models.lookups import (
    Exact,
    GreaterThan,
    GreaterThanOrEqual,
    In,
    IsNull,
    LessThan,
    LessThanOrEqual,
    Lookup,
)

class MultiColSource:
    alias: str
    field: Field
    sources: tuple[Field, Field]
    targets: tuple[Field, Field]
    contains_aggregate: bool
    output_field: Field
    def __init__(
        self, alias: str, targets: tuple[Field, Field], sources: tuple[Field, Field], field: Field
    ) -> None: ...
    def relabeled_clone(self, relabels: Mapping[str, str]) -> MultiColSource: ...
    def get_lookup(self, lookup: str) -> type[Lookup] | None: ...

def get_normalized_value(value: Any, lhs: Any) -> tuple[Any, ...]: ...

class RelatedIn(In):
    bilateral_transforms: list[Any]
    lhs: Any
    rhs: Any
    def get_prep_lookup(self) -> Iterable[Any]: ...

class RelatedLookupMixin:
    rhs: Any
    def get_prep_lookup(self) -> Any: ...

class RelatedExact(RelatedLookupMixin, Exact): ...
class RelatedLessThan(RelatedLookupMixin, LessThan): ...
class RelatedGreaterThan(RelatedLookupMixin, GreaterThan): ...
class RelatedGreaterThanOrEqual(RelatedLookupMixin, GreaterThanOrEqual): ...
class RelatedLessThanOrEqual(RelatedLookupMixin, LessThanOrEqual): ...
class RelatedIsNull(RelatedLookupMixin, IsNull): ...

from collections import defaultdict
from collections.abc import Callable, Iterable, Iterator, Sequence
from typing import Any

from django.db import IntegrityError
from django.db.models.base import Model
from django.db.models.fields import Field
from django.db.models.options import Options
from django.db.models.query import QuerySet
from django.utils.datastructures import _IndexableCollection

def CASCADE(
    collector: Collector,
    field: Field[Any, Any],
    sub_objs: QuerySet[Model],
    using: str,
) -> None: ...
def SET_NULL(
    collector: Collector,
    field: Field[Any, Any],
    sub_objs: QuerySet[Model],
    using: str,
) -> None: ...
def SET_DEFAULT(
    collector: Collector,
    field: Field[Any, Any],
    sub_objs: QuerySet[Model],
    using: str,
) -> None: ...
def DO_NOTHING(
    collector: Collector,
    field: Field[Any, Any],
    sub_objs: QuerySet[Model],
    using: str,
) -> None: ...
def PROTECT(
    collector: Collector,
    field: Field[Any, Any],
    sub_objs: QuerySet[Model],
    using: str,
) -> None: ...
def RESTRICT(
    collector: Collector,
    field: Field[Any, Any],
    sub_objs: QuerySet[Model],
    using: str,
) -> None: ...
def SET(value: Any) -> Callable[..., Any]: ...
def get_candidate_relations_to_delete(opts: Options) -> Iterable[Field]: ...

class ProtectedError(IntegrityError):
    protected_objects: set[Model]
    def __init__(self, msg: str, protected_objects: set[Model]) -> None: ...

class RestrictedError(IntegrityError):
    restricted_objects: set[Model]
    def __init__(self, msg: str, restricted_objects: set[Model]) -> None: ...

class Collector:
    using: str
    origin: Model | QuerySet[Model] | None
    data: dict[type[Model], set[Model] | list[Model]]
    field_updates: defaultdict[tuple[Field, Any], list[Model]]
    restricted_objects: defaultdict[Model, defaultdict[Field, set[Model]]]
    fast_deletes: list[QuerySet[Model]]
    dependencies: defaultdict[Model, set[Model]]
    def __init__(self, using: str, origin: Model | QuerySet[Model] | None = None) -> None: ...
    def add(
        self,
        objs: _IndexableCollection[Model],
        source: type[Model] | None = ...,
        nullable: bool = ...,
        reverse_dependency: bool = ...,
    ) -> list[Model]: ...
    def add_dependency(self, model: type[Model], dependency: type[Model], reverse_dependency: bool = ...) -> None: ...
    def add_field_update(self, field: Field, value: Any, objs: _IndexableCollection[Model]) -> None: ...
    def add_restricted_objects(self, field: Field, objs: _IndexableCollection[Model]) -> None: ...
    def clear_restricted_objects_from_set(self, model: type[Model], objs: set[Model]) -> None: ...
    def clear_restricted_objects_from_queryset(self, model: type[Model], qs: QuerySet[Model]) -> None: ...
    def can_fast_delete(self, objs: Model | Iterable[Model], from_field: Field | None = ...) -> bool: ...
    def get_del_batches(
        self, objs: _IndexableCollection[Model], fields: Iterable[Field]
    ) -> Sequence[Sequence[Model]]: ...
    def collect(
        self,
        objs: _IndexableCollection[Model | None],
        source: type[Model] | None = ...,
        nullable: bool = ...,
        collect_related: bool = ...,
        source_attr: str | None = ...,
        reverse_dependency: bool = ...,
        keep_parents: bool = ...,
        fail_on_restricted: bool = ...,
    ) -> None: ...
    def related_objects(
        self, related_model: type[Model], related_fields: Iterable[Field], objs: _IndexableCollection[Model]
    ) -> QuerySet[Model]: ...
    def instances_with_model(self) -> Iterator[tuple[type[Model], Model]]: ...
    def sort(self) -> None: ...
    def delete(self) -> tuple[int, dict[str, int]]: ...

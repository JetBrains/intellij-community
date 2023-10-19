from typing import Any

from ...sql.dml import Insert as StandardInsert
from ...sql.elements import ClauseElement
from ...util import memoized_property

class Insert(StandardInsert):
    stringify_dialect: str
    inherit_cache: bool
    @memoized_property
    def excluded(self): ...
    def on_conflict_do_update(
        self,
        constraint: Any | None = ...,
        index_elements: Any | None = ...,
        index_where: Any | None = ...,
        set_: Any | None = ...,
        where: Any | None = ...,
    ) -> None: ...
    def on_conflict_do_nothing(
        self, constraint: Any | None = ..., index_elements: Any | None = ..., index_where: Any | None = ...
    ) -> None: ...

insert: Any

class OnConflictClause(ClauseElement):
    stringify_dialect: str
    constraint_target: Any
    inferred_target_elements: Any
    inferred_target_whereclause: Any
    def __init__(self, constraint: Any | None = ..., index_elements: Any | None = ..., index_where: Any | None = ...) -> None: ...

class OnConflictDoNothing(OnConflictClause):
    __visit_name__: str

class OnConflictDoUpdate(OnConflictClause):
    __visit_name__: str
    update_values_to_set: Any
    update_whereclause: Any
    def __init__(
        self,
        constraint: Any | None = ...,
        index_elements: Any | None = ...,
        index_where: Any | None = ...,
        set_: Any | None = ...,
        where: Any | None = ...,
    ) -> None: ...

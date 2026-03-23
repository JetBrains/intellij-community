from collections.abc import Iterable
from typing import Any

from django.db.models.base import Model
from django.db.models.expressions import Case
from django.db.models.fields import Field
from django.db.models.sql.query import Query
from django.db.models.sql.where import WhereNode

class DeleteQuery(Query):
    def do_query(self, table: str, where: WhereNode, using: str) -> int: ...
    def delete_batch(self, pk_list: list[int] | list[str], using: str) -> int: ...

class UpdateQuery(Query):
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    def update_batch(self, pk_list: list[int], values: dict[str, int | None], using: str) -> None: ...
    def add_update_values(self, values: dict[str, Any]) -> None: ...
    def add_update_fields(self, values_seq: list[tuple[Field, type[Model] | None, Case]]) -> None: ...
    def add_related_update(self, model: type[Model], field: Field, value: int | str) -> None: ...
    def get_related_updates(self) -> list[UpdateQuery]: ...

class InsertQuery(Query):
    fields: Iterable[Field]
    objs: list[Model]
    raw: bool
    def __init__(
        self,
        *args: Any,
        on_conflict: Any | None = ...,
        update_fields: Any | None = ...,
        unique_fields: Any | None = ...,
        **kwargs: Any,
    ) -> None: ...
    def insert_values(self, fields: Iterable[Field], objs: list[Model], raw: bool = False) -> None: ...

class AggregateQuery(Query):
    inner_query: Query
    def __init__(self, model: type[Model], inner_query: Query) -> None: ...

__all__ = ["AggregateQuery", "DeleteQuery", "InsertQuery", "UpdateQuery"]

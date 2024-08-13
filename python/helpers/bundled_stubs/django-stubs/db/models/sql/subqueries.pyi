from collections.abc import Iterable
from typing import Any

from django.db.models.base import Model
from django.db.models.expressions import Case
from django.db.models.fields import Field
from django.db.models.sql.query import Query
from django.db.models.sql.where import WhereNode

class DeleteQuery(Query):
    select: tuple
    where_class: type[WhereNode]
    where: WhereNode
    def do_query(self, table: str, where: WhereNode, using: str) -> int: ...
    def delete_batch(self, pk_list: list[int] | list[str], using: str) -> int: ...

class UpdateQuery(Query):
    select: tuple
    where_class: type[WhereNode]
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    where: WhereNode
    def update_batch(self, pk_list: list[int], values: dict[str, int | None], using: str) -> None: ...
    def add_update_values(self, values: dict[str, Any]) -> None: ...
    def add_update_fields(self, values_seq: list[tuple[Field, type[Model] | None, Case]]) -> None: ...
    def add_related_update(self, model: type[Model], field: Field, value: int | str) -> None: ...
    def get_related_updates(self) -> list[UpdateQuery]: ...

class InsertQuery(Query):
    select: tuple
    where: WhereNode
    where_class: type[WhereNode]
    fields: Iterable[Field]
    objs: list[Model]
    raw: bool
    def __init__(self, *args: Any, ignore_conflicts: bool = ..., **kwargs: Any) -> None: ...
    def insert_values(self, fields: Iterable[Field], objs: list[Model], raw: bool = ...) -> None: ...

class AggregateQuery(Query):
    select: tuple
    sub_params: tuple
    where: WhereNode
    where_class: type[WhereNode]

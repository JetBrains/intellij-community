from typing import Any

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.fields.mixins import FieldCacheMixin
from django.db.models.query_utils import FilteredRelation, PathInfo
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType

class MultiJoin(Exception):
    level: int
    names_with_path: list[tuple[str, list[PathInfo]]]
    def __init__(self, names_pos: int, path_with_names: list[tuple[str, list[PathInfo]]]) -> None: ...

class Empty: ...

class Join:
    table_name: str
    parent_alias: str
    table_alias: str | None
    join_type: str
    join_cols: tuple
    join_field: FieldCacheMixin
    nullable: bool
    filtered_relation: FilteredRelation | None
    def __init__(
        self,
        table_name: str,
        parent_alias: str,
        table_alias: str | None,
        join_type: str,
        join_field: FieldCacheMixin,
        nullable: bool,
        filtered_relation: FilteredRelation | None = None,
    ) -> None: ...
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...
    def relabeled_clone(self, change_map: dict[str | None, str]) -> Join: ...
    def demote(self) -> Join: ...
    def promote(self) -> Join: ...

class BaseTable:
    join_type: Any
    parent_alias: Any
    filtered_relation: Any
    table_name: str
    table_alias: str | None
    def __init__(self, table_name: str, alias: str | None) -> None: ...
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...
    def relabeled_clone(self, change_map: dict[str | None, str]) -> BaseTable: ...

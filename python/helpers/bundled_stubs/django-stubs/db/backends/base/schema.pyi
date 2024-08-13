from collections.abc import Sequence
from contextlib import AbstractContextManager
from logging import Logger
from types import TracebackType
from typing import Any

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.backends.ddl_references import Statement
from django.db.models.base import Model
from django.db.models.constraints import BaseConstraint
from django.db.models.fields import Field
from django.db.models.indexes import Index
from django.db.models.sql.compiler import _AsSqlType
from typing_extensions import Self

logger: Logger

class BaseDatabaseSchemaEditor(AbstractContextManager[Any]):
    sql_create_table: str
    sql_rename_table: str
    sql_retablespace_table: str
    sql_delete_table: str
    sql_create_column: str
    sql_alter_column: str
    sql_alter_column_type: str
    sql_alter_column_null: str
    sql_alter_column_not_null: str
    sql_alter_column_default: str
    sql_alter_column_no_default: str
    sql_alter_column_no_default_null: str
    sql_delete_column: str
    sql_rename_column: str
    sql_update_with_default: str
    sql_unique_constraint: str
    sql_check_constraint: str
    sql_delete_constraint: str
    sql_constraint: str
    sql_create_check: str
    sql_delete_check: str
    sql_create_unique: str
    sql_delete_unique: str

    sql_create_fk: str
    sql_create_inline_fk: str | None
    sql_create_column_inline_fk: str | None
    sql_delete_fk: str

    sql_create_index: str
    sql_create_unique_index: str
    sql_rename_index: str
    sql_delete_index: str
    sql_create_pk: str
    sql_delete_pk: str
    sql_delete_procedure: str
    sql_alter_table_comment: str
    sql_alter_column_comment: str
    connection: BaseDatabaseWrapper
    collect_sql: bool
    collected_sql: Any
    atomic_migration: Any
    def __init__(self, connection: BaseDatabaseWrapper, collect_sql: bool = ..., atomic: bool = ...) -> None: ...
    deferred_sql: Any
    atomic: Any
    def __enter__(self) -> Self: ...
    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        exc_tb: TracebackType | None,
    ) -> None: ...
    def execute(self, sql: Statement | str, params: Sequence[Any] | None = ...) -> None: ...
    def quote_name(self, name: str) -> str: ...
    def table_sql(self, model: type[Model]) -> tuple[str, list[Any]]: ...
    def column_sql(
        self, model: type[Model], field: Field, include_default: bool = ...
    ) -> tuple[None, None] | tuple[str, list[Any]]: ...
    def skip_default(self, field: Field) -> bool: ...
    def skip_default_on_alter(self, field: Field) -> bool: ...
    def prepare_default(self, value: Any) -> Any: ...
    def db_default_sql(self, field: Field) -> _AsSqlType: ...
    def effective_default(self, field: Field) -> int | str: ...
    def quote_value(self, value: Any) -> str: ...
    def create_model(self, model: type[Model]) -> None: ...
    def delete_model(self, model: type[Model]) -> None: ...
    def add_index(self, model: type[Model], index: Index) -> None: ...
    def remove_index(self, model: type[Model], index: Index) -> None: ...
    def rename_index(self, model: type[Model], old_index: Index, new_index: Index) -> None: ...
    def add_constraint(self, model: type[Model], constraint: BaseConstraint) -> None: ...
    def remove_constraint(self, model: type[Model], constraint: BaseConstraint) -> None: ...
    def alter_unique_together(
        self,
        model: type[Model],
        old_unique_together: Sequence[Sequence[str]],
        new_unique_together: Sequence[Sequence[str]],
    ) -> None: ...
    def alter_index_together(
        self,
        model: type[Model],
        old_index_together: Sequence[Sequence[str]],
        new_index_together: Sequence[Sequence[str]],
    ) -> None: ...
    def alter_db_table(self, model: type[Model], old_db_table: str, new_db_table: str) -> None: ...
    def alter_db_table_comment(
        self, model: type[Model], old_db_table_comment: str, new_db_table_comment: str
    ) -> None: ...
    def alter_db_tablespace(self, model: type[Model], old_db_tablespace: str, new_db_tablespace: str) -> None: ...
    def add_field(self, model: type[Model], field: Field) -> None: ...
    def remove_field(self, model: type[Model], field: Field) -> None: ...
    def alter_field(self, model: type[Model], old_field: Field, new_field: Field, strict: bool = ...) -> None: ...
    def remove_procedure(self, procedure_name: Any, param_types: Any = ...) -> None: ...

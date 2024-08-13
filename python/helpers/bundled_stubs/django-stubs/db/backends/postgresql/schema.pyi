from typing import Any

from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from django.db.backends.postgresql.base import DatabaseWrapper
from django.db.models.base import Model
from django.db.models.indexes import Index

class DatabaseSchemaEditor(BaseDatabaseSchemaEditor):
    connection: DatabaseWrapper
    sql_create_sequence: str
    sql_delete_sequence: str
    sql_set_sequence_max: str
    sql_set_sequence_owner: str
    sql_create_index: str
    sql_create_index_concurrently: str
    sql_delete_index: str
    sql_delete_index_concurrently: str
    sql_create_column_inline_fk: str
    sql_delete_fk: str
    sql_delete_procedure: str
    def quote_value(self, value: Any) -> str: ...
    def add_index(self, model: type[Model], index: Index, concurrently: bool = ...) -> None: ...
    def remove_index(self, model: type[Model], index: Index, concurrently: bool = ...) -> None: ...

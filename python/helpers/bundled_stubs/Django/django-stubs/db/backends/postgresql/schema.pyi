from typing import Any

from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from django.db.backends.postgresql.base import DatabaseWrapper
from django.db.models.base import Model
from django.db.models.indexes import Index
from typing_extensions import override

class DatabaseSchemaEditor(BaseDatabaseSchemaEditor):
    connection: DatabaseWrapper
    sql_delete_sequence: str
    sql_alter_sequence_type: str
    sql_create_index: str
    sql_create_index_concurrently: str
    sql_delete_index: str
    sql_delete_index_concurrently: str
    sql_create_column_inline_fk: str
    sql_delete_fk: str
    sql_delete_procedure: str
    sql_add_identity: str
    sql_drop_indentity: str  # typo in source: `indentity` instead of `identity`
    @override
    def quote_value(self, value: Any) -> str: ...
    @override
    def add_index(self, model: type[Model], index: Index, concurrently: bool = False) -> None: ...
    @override
    def remove_index(self, model: type[Model], index: Index, concurrently: bool = False) -> None: ...

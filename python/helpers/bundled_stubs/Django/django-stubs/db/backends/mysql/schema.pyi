from typing import Any

from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from django.db.backends.mysql.base import DatabaseWrapper
from django.db.models.base import Model
from django.db.models.fields import Field

class DatabaseSchemaEditor(BaseDatabaseSchemaEditor):
    connection: DatabaseWrapper
    sql_rename_table: str
    sql_alter_column_null: str
    sql_alter_column_not_null: str
    sql_alter_column_type: str
    sql_delete_column: str
    sql_delete_unique: str
    sql_create_column_inline_fk: str
    sql_delete_fk: str
    sql_delete_index: str
    sql_create_pk: str
    sql_delete_pk: str
    sql_create_index: str
    @property
    def sql_delete_check(self) -> str: ...  # type: ignore[override]
    @property
    def sql_rename_column(self) -> str: ...  # type: ignore[override]
    def quote_value(self, value: Any) -> str: ...
    def skip_default(self, field: Field) -> bool: ...
    def add_field(self, model: type[Model], field: Field) -> None: ...

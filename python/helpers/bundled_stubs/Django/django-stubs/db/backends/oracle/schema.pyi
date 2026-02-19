from typing import Any

from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from django.db.backends.oracle.base import DatabaseWrapper
from django.db.models.base import Model
from django.db.models.fields import Field

class DatabaseSchemaEditor(BaseDatabaseSchemaEditor):
    connection: DatabaseWrapper
    sql_create_column: str
    sql_alter_column_type: str
    sql_alter_column_null: str
    sql_alter_column_not_null: str
    sql_alter_column_default: str
    sql_alter_column_no_default: str
    sql_delete_column: str
    sql_create_column_inline_fk: str
    sql_delete_table: str
    sql_create_index: str
    def quote_value(self, value: Any) -> str: ...
    def remove_field(self, model: type[Model], field: Field) -> None: ...
    def delete_model(self, model: type[Model]) -> None: ...
    def alter_field(self, model: type[Model], old_field: Field, new_field: Field, strict: bool = False) -> None: ...
    def normalize_name(self, name: Any) -> str: ...
    def prepare_default(self, value: Any) -> Any: ...

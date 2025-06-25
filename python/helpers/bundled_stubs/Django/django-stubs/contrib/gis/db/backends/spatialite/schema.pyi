from typing import Any

from django.db.backends.sqlite3.schema import DatabaseSchemaEditor
from django.db.models.base import Model
from django.db.models.fields import Field

class SpatialiteSchemaEditor(DatabaseSchemaEditor):
    sql_add_geometry_column: str
    sql_add_spatial_index: str
    sql_drop_spatial_index: str
    sql_recover_geometry_metadata: str
    sql_remove_geometry_metadata: str
    sql_discard_geometry_columns: str
    sql_update_geometry_columns: str
    geometry_tables: Any
    geometry_sql: Any
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    def geo_quote_name(self, name: Any) -> Any: ...
    def column_sql(
        self, model: type[Model], field: Field, include_default: bool = False
    ) -> tuple[None, None] | tuple[str, list[Any]]: ...
    def remove_geometry_metadata(self, model: type[Model], field: Field) -> None: ...
    def create_model(self, model: type[Model]) -> None: ...
    def delete_model(self, model: type[Model], **kwargs: Any) -> None: ...
    def add_field(self, model: type[Model], field: Field) -> None: ...
    def remove_field(self, model: type[Model], field: Field) -> None: ...
    def alter_db_table(
        self,
        model: type[Model],
        old_db_table: str,
        new_db_table: str,
    ) -> None: ...

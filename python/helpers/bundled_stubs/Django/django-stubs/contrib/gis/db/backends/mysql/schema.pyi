from logging import Logger
from typing import Any

from django.db.backends.mysql.schema import DatabaseSchemaEditor
from django.db.models.base import Model
from django.db.models.fields import Field
from typing_extensions import override

logger: Logger

class MySQLGISSchemaEditor(DatabaseSchemaEditor):
    sql_add_spatial_index: str
    @override
    def skip_default(self, field: Field) -> bool: ...
    @override
    def column_sql(
        self, model: type[Model], field: Field, include_default: bool = ...
    ) -> tuple[None, None] | tuple[str, list[Any]]: ...
    @override
    def create_model(self, model: type[Model]) -> None: ...
    @override
    def add_field(self, model: type[Model], field: Field) -> None: ...
    @override
    def remove_field(self, model: type[Model], field: Field) -> None: ...

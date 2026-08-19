from collections.abc import Callable
from decimal import Decimal
from typing import Any
from uuid import UUID

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.backends.base.operations import BaseDatabaseOperations
from django.db.models.aggregates import Aggregate
from django.db.models.expressions import Expression
from django.db.models.fields import DateField, DateTimeField, TimeField
from typing_extensions import override

UNSUPPORTED_DATETIME_AGGREGATES: tuple[type[Aggregate], ...]
DATETIME_FIELDS: tuple[type[DateField[Any, Any] | DateTimeField[Any, Any] | TimeField[Any, Any]], ...]

class DatabaseOperations(BaseDatabaseOperations):
    jsonfield_datatype_values: frozenset[str]
    def convert_datetimefield_value(self, value: Any, expression: Any, connection: Any) -> Any | None: ...
    def convert_datefield_value(self, value: Any, expression: Any, connection: Any) -> Any: ...
    def convert_timefield_value(self, value: Any, expression: Any, connection: Any) -> Any: ...
    def get_decimalfield_converter(
        self,
        expression: Any,
    ) -> Callable[[float | None, Expression, BaseDatabaseWrapper], Decimal | None]: ...
    def convert_uuidfield_value(self, value: Any, expression: Any, connection: Any) -> UUID | None: ...
    def convert_booleanfield_value(self, value: Any, expression: Any, connection: Any) -> Any: ...
    @override
    def format_json_path_numeric_index(self, num: int) -> str: ...

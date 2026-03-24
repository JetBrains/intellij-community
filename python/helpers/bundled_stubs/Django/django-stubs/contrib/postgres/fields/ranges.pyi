from typing import Any, ClassVar, Literal, TypeVar

from _typeshed import Unused
from django.contrib.postgres import forms
from django.contrib.postgres.utils import CheckPostgresInstalledMixin
from django.db import models
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.lookups import PostgresOperatorLookup
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType
from psycopg2.extras import DateRange, DateTimeTZRange, NumericRange, Range  # type: ignore[import-untyped]
from typing_extensions import override

class RangeBoundary(models.Expression):
    lower: str
    upper: str
    def __init__(self, inclusive_lower: bool = True, inclusive_upper: bool = False) -> None: ...

class RangeOperators:
    EQUAL: Literal["="]
    NOT_EQUAL: Literal["<>"]
    CONTAINS: Literal["@>"]
    CONTAINED_BY: Literal["<@"]
    OVERLAPS: Literal["&&"]
    FULLY_LT: Literal["<<"]
    FULLY_GT: Literal[">>"]
    NOT_LT: Literal["&>"]
    NOT_GT: Literal["&<"]
    ADJACENT_TO: Literal["-|-"]

_RangeT = TypeVar("_RangeT", bound=Range[Any])

class RangeField(CheckPostgresInstalledMixin, models.Field[Any, _RangeT]):
    empty_strings_allowed: bool
    base_field: type[models.Field]
    range_type: type[_RangeT]
    def get_placeholder(self, value: Unused, compiler: Unused, connection: BaseDatabaseWrapper) -> str: ...
    @override
    def get_prep_value(self, value: Any) -> Any | None: ...
    @override
    def to_python(self, value: Any) -> Any: ...
    @override
    def value_to_string(self, obj: models.Model) -> str | None: ...  # type: ignore[override]
    @override
    def formfield(self, **kwargs: Any) -> Any: ...  # type: ignore[override]

class ContinuousRangeField(RangeField[_RangeT]):
    default_bounds: str
    def __init__(self, *args: Any, default_bounds: str = "[)", **kwargs: Any) -> None: ...

class IntegerRangeField(RangeField[NumericRange]):
    base_field: type[models.IntegerField]
    form_field: type[forms.IntegerRangeField]

class BigIntegerRangeField(RangeField[NumericRange]):
    base_field: type[models.BigIntegerField]
    form_field: type[forms.IntegerRangeField]

class DecimalRangeField(ContinuousRangeField[NumericRange]):
    base_field: type[models.DecimalField]
    form_field: type[forms.DecimalRangeField]

class DateTimeRangeField(ContinuousRangeField[DateTimeTZRange]):
    base_field: type[models.DateTimeField]
    form_field: type[forms.DateTimeRangeField]

class DateRangeField(RangeField[DateRange]):
    base_field: type[models.DateField]
    form_field: type[forms.DateRangeField]

class DateTimeRangeContains(PostgresOperatorLookup): ...

class RangeContainedBy(PostgresOperatorLookup):
    type_mapping: dict[str, str]
    @override
    def process_lhs(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...  # type: ignore[override]

class FullyLessThan(PostgresOperatorLookup): ...
class FullGreaterThan(PostgresOperatorLookup): ...
class NotLessThan(PostgresOperatorLookup): ...
class NotGreaterThan(PostgresOperatorLookup): ...
class AdjacentToLookup(PostgresOperatorLookup): ...

class RangeStartsWith(models.Transform):
    @property
    @override
    def output_field(self) -> models.Field: ...

class RangeEndsWith(models.Transform):
    @property
    @override
    def output_field(self) -> models.Field: ...

class IsEmpty(models.Transform):
    output_field: ClassVar[models.BooleanField]

class LowerInclusive(models.Transform):
    output_field: ClassVar[models.BooleanField]

class LowerInfinite(models.Transform):
    output_field: ClassVar[models.BooleanField]

class UpperInclusive(models.Transform):
    output_field: ClassVar[models.BooleanField]

class UpperInfinite(models.Transform):
    output_field: ClassVar[models.BooleanField]

__all__ = [
    "BigIntegerRangeField",
    "DateRangeField",
    "DateTimeRangeField",
    "DecimalRangeField",
    "IntegerRangeField",
    "RangeBoundary",
    "RangeField",
    "RangeOperators",
]

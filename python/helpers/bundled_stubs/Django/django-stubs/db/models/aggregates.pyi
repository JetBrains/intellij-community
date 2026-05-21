from collections.abc import Sequence
from typing import Any, ClassVar

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.expressions import Combinable, Func
from django.db.models.fields import IntegerField
from django.db.models.functions.mixins import FixDurationInputMixin, NumericOutputFieldMixin
from django.db.models.query import _OrderByFieldName
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType
from typing_extensions import override

class Aggregate(Func):
    name: str | None
    filter: Any
    allow_distinct: bool
    allow_order_by: bool
    empty_result_set_value: int | None
    def __init__(
        self,
        *expressions: Any,
        distinct: bool = False,
        filter: Any | None = None,
        default: Any | None = None,
        order_by: Sequence[_OrderByFieldName] | None = None,
        **extra: Any,
    ) -> None: ...
    @property
    def default_alias(self) -> str: ...
    @override
    def as_sql(  # type: ignore[override]
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any
    ) -> _AsSqlType: ...

class AnyValue(Aggregate):
    @override
    def as_sql(  # type: ignore[override]
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any
    ) -> _AsSqlType: ...

class Avg(FixDurationInputMixin, NumericOutputFieldMixin, Aggregate): ...

class Count(Aggregate):
    output_field: ClassVar[IntegerField]
    def __init__(self, expression: Any, filter: Any | None = None, **extra: Any) -> None: ...

class Max(Aggregate): ...
class Min(Aggregate): ...

class StdDev(NumericOutputFieldMixin, Aggregate):
    def __init__(self, expression: Any, sample: bool = False, **extra: Any) -> None: ...

class StringAgg(Aggregate):
    def __init__(self, expression: Combinable | str, delimiter: str | Combinable, **extra: Any) -> None: ...
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...
    def as_mysql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...
    @override
    def as_sqlite(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class Sum(FixDurationInputMixin, Aggregate): ...

class Variance(NumericOutputFieldMixin, Aggregate):
    def __init__(self, expression: Any, sample: bool = False, **extra: Any) -> None: ...

__all__ = ["Aggregate", "AnyValue", "Avg", "Count", "Max", "Min", "StdDev", "StringAgg", "Sum", "Variance"]

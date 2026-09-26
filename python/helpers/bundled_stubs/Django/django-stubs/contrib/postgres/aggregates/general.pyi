from collections.abc import Sequence
from typing import Any, ClassVar, Self

from django.contrib.postgres.fields import ArrayField
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models import Aggregate, BooleanField, JSONField, TextField
from django.db.models.expressions import BaseExpression, Combinable
from django.db.models.query import _OrderByFieldName
from django.db.models.query_utils import Q
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType
from typing_extensions import deprecated, override

from .mixins import OrderableAggMixin

class ArrayAgg(OrderableAggMixin, Aggregate):
    @property
    @override
    def output_field(self) -> ArrayField[Any, Any]: ...
    @override
    def resolve_expression(
        self,
        query: Any = ...,
        allow_joins: bool = ...,
        reuse: set[str] | None = ...,
        summarize: bool = ...,
        for_save: bool = ...,
    ) -> Self: ...
    @override
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...  # type: ignore[override]

class BitAnd(Aggregate):
    @override
    @deprecated(
        "The PostgreSQL-specific BitAnd function is deprecated. Use django.db.models.aggregates.BitAnd instead."
    )
    def __init__(self, expression: Any, **extra: Any) -> None: ...

class BitOr(Aggregate):
    @override
    @deprecated("The PostgreSQL-specific BitOr function is deprecated. Use django.db.models.aggregates.BitOr instead.")
    def __init__(self, expression: Any, **extra: Any) -> None: ...

class BitXor(Aggregate):
    @override
    @deprecated(
        "The PostgreSQL-specific BitXor function is deprecated. Use django.db.models.aggregates.BitXor instead."
    )
    def __init__(self, expression: Any, **extra: Any) -> None: ...

class BoolAnd(Aggregate):
    output_field: ClassVar[BooleanField[Any, Any]]

class BoolOr(Aggregate):
    output_field: ClassVar[BooleanField[Any, Any]]

class JSONBAgg(OrderableAggMixin, Aggregate):
    output_field: ClassVar[JSONField[Any, Any]]
    @override
    def resolve_expression(
        self,
        query: Any = ...,
        allow_joins: bool = ...,
        reuse: set[str] | None = ...,
        summarize: bool = ...,
        for_save: bool = ...,
    ) -> Self: ...
    @override
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...  # type: ignore[override]

class StringAgg(OrderableAggMixin, Aggregate):
    output_field: ClassVar[TextField[Any, Any]]
    @deprecated(
        "The PostgreSQL specific StringAgg function is deprecated. Use django.db.models.aggregates.StringAgg instead."
    )
    def __init__(
        self,
        expression: BaseExpression | Combinable | str,
        delimiter: Any,
        *,
        distinct: bool = False,
        filter: Q | BaseExpression | None = None,
        default: Any | None = None,
        ordering: _OrderByFieldName | Sequence[_OrderByFieldName] = ...,
        order_by: _OrderByFieldName | Sequence[_OrderByFieldName] = ...,
        **extra: Any,
    ) -> None: ...
    @override
    def resolve_expression(
        self,
        query: Any = ...,
        allow_joins: bool = ...,
        reuse: set[str] | None = ...,
        summarize: bool = ...,
        for_save: bool = ...,
    ) -> Self: ...
    @override
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...  # type: ignore[override]

__all__ = ["ArrayAgg", "BitAnd", "BitOr", "BitXor", "BoolAnd", "BoolOr", "JSONBAgg", "StringAgg"]

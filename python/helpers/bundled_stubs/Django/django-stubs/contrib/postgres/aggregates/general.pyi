from collections.abc import Sequence
from typing import Any, ClassVar

from django.contrib.postgres.fields import ArrayField
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models import Aggregate, BooleanField, JSONField, TextField
from django.db.models.expressions import BaseExpression, Combinable
from django.db.models.query import _OrderByFieldName
from django.db.models.query_utils import Q
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType
from typing_extensions import Self, override

from .mixins import OrderableAggMixin

class ArrayAgg(OrderableAggMixin, Aggregate):
    @property
    @override
    def output_field(self) -> ArrayField: ...
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

class BitAnd(Aggregate): ...
class BitOr(Aggregate): ...
class BitXor(Aggregate): ...

class BoolAnd(Aggregate):
    output_field: ClassVar[BooleanField]

class BoolOr(Aggregate):
    output_field: ClassVar[BooleanField]

class JSONBAgg(OrderableAggMixin, Aggregate):
    output_field: ClassVar[JSONField]
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
    output_field: ClassVar[TextField]
    def __init__(
        self,
        expression: BaseExpression | Combinable | str,
        delimiter: Any,
        *,
        distinct: bool = False,
        filter: Q | None = None,
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

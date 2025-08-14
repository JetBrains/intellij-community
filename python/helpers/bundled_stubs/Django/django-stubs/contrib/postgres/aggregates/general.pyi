from typing import Any, ClassVar

from django.contrib.postgres.fields import ArrayField
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models import Aggregate, BooleanField, JSONField, TextField
from django.db.models.expressions import BaseExpression, Combinable
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType
from typing_extensions import Self

from .mixins import OrderableAggMixin

class ArrayAgg(OrderableAggMixin, Aggregate):
    @property
    def output_field(self) -> ArrayField: ...
    def resolve_expression(
        self,
        query: Any = ...,
        allow_joins: bool = ...,
        reuse: set[str] | None = ...,
        summarize: bool = ...,
        for_save: bool = ...,
    ) -> Self: ...
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
    def __init__(
        self, *expressions: BaseExpression | Combinable | str, default: Any | None = ..., **extra: Any
    ) -> None: ...
    def resolve_expression(
        self,
        query: Any = ...,
        allow_joins: bool = ...,
        reuse: set[str] | None = ...,
        summarize: bool = ...,
        for_save: bool = ...,
    ) -> Self: ...
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...  # type: ignore[override]

class StringAgg(OrderableAggMixin, Aggregate):
    output_field: ClassVar[TextField]
    def __init__(self, expression: BaseExpression | Combinable | str, delimiter: Any, **extra: Any) -> None: ...
    def resolve_expression(
        self,
        query: Any = ...,
        allow_joins: bool = ...,
        reuse: set[str] | None = ...,
        summarize: bool = ...,
        for_save: bool = ...,
    ) -> Self: ...
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...  # type: ignore[override]

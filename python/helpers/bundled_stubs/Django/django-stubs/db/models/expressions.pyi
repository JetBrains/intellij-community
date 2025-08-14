import datetime
from collections.abc import Callable, Iterable, Iterator, Mapping, Sequence
from decimal import Decimal
from typing import Any, ClassVar, Generic, Literal, NoReturn, TypeVar

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models import Q, fields
from django.db.models.fields import Field
from django.db.models.lookups import Lookup, Transform
from django.db.models.query import QuerySet
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType
from django.db.models.sql.query import Query
from django.utils.deconstruct import _Deconstructible
from django.utils.functional import cached_property
from typing_extensions import Self, TypeAlias

class SQLiteNumericMixin:
    def as_sqlite(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

_Numeric: TypeAlias = float | Decimal

class Combinable:
    ADD: str
    SUB: str
    MUL: str
    DIV: str
    POW: str
    MOD: str
    BITAND: str
    BITOR: str
    BITLEFTSHIFT: str
    BITRIGHTSHIFT: str
    BITXOR: str
    def __neg__(self) -> CombinedExpression: ...
    def __add__(self, other: datetime.timedelta | Combinable | _Numeric | str | None) -> CombinedExpression: ...
    def __sub__(self, other: datetime.timedelta | Combinable | _Numeric) -> CombinedExpression: ...
    def __mul__(self, other: datetime.timedelta | Combinable | _Numeric) -> CombinedExpression: ...
    def __truediv__(self, other: Combinable | _Numeric) -> CombinedExpression: ...
    def __mod__(self, other: int | Combinable) -> CombinedExpression: ...
    def __pow__(self, other: _Numeric | Combinable) -> CombinedExpression: ...
    def __and__(self, other: Combinable | Q) -> Q: ...
    def bitand(self, other: int) -> CombinedExpression: ...
    def bitleftshift(self, other: int) -> CombinedExpression: ...
    def bitrightshift(self, other: int) -> CombinedExpression: ...
    def __xor__(self, other: Combinable | Q) -> Q: ...
    def bitxor(self, other: int) -> CombinedExpression: ...
    def __or__(self, other: Combinable | Q) -> Q: ...
    def bitor(self, other: int) -> CombinedExpression: ...
    def __radd__(self, other: datetime.datetime | _Numeric | Combinable | None) -> CombinedExpression: ...
    def __rsub__(self, other: _Numeric | Combinable) -> CombinedExpression: ...
    def __rmul__(self, other: _Numeric | Combinable) -> CombinedExpression: ...
    def __rtruediv__(self, other: _Numeric | Combinable) -> CombinedExpression: ...
    def __rmod__(self, other: int | Combinable) -> CombinedExpression: ...
    def __rpow__(self, other: _Numeric | Combinable) -> CombinedExpression: ...
    def __rand__(self, other: Any) -> Combinable: ...
    def __ror__(self, other: Any) -> Combinable: ...
    def __rxor__(self, other: Any) -> Combinable: ...
    def __invert__(self) -> NegatedExpression[Combinable]: ...

class BaseExpression:
    is_summary: bool
    filterable: bool
    window_compatible: bool
    allowed_default: bool
    def __init__(self, output_field: Field | None = None) -> None: ...
    def get_db_converters(self, connection: BaseDatabaseWrapper) -> list[Callable]: ...
    def get_source_expressions(self) -> list[Any]: ...
    def set_source_expressions(self, exprs: Sequence[Combinable | Expression]) -> None: ...
    @cached_property
    def contains_aggregate(self) -> bool: ...
    @cached_property
    def contains_over_clause(self) -> bool: ...
    @cached_property
    def contains_column_references(self) -> bool: ...
    @cached_property
    def contains_subquery(self) -> bool: ...
    def resolve_expression(
        self,
        query: Any | None = None,
        allow_joins: bool = True,
        reuse: set[str] | None = None,
        summarize: bool = False,
        for_save: bool = False,
    ) -> Self: ...
    @property
    def conditional(self) -> bool: ...
    @property
    def field(self) -> Field: ...
    @cached_property
    def output_field(self) -> Field: ...
    @cached_property
    def convert_value(self) -> Callable: ...
    def get_lookup(self, lookup: str) -> type[Lookup] | None: ...
    def get_transform(self, name: str) -> type[Transform] | None: ...
    def relabeled_clone(self, change_map: Mapping[str, str]) -> Self: ...
    def copy(self) -> Self: ...
    def get_group_by_cols(self) -> list[BaseExpression]: ...
    def get_source_fields(self) -> list[Field | None]: ...
    def asc(
        self,
        *,
        descending: bool = ...,
        nulls_first: bool | None = ...,
        nulls_last: bool | None = ...,
    ) -> OrderBy: ...
    def desc(
        self,
        *,
        nulls_first: bool | None = ...,
        nulls_last: bool | None = ...,
    ) -> OrderBy: ...
    def reverse_ordering(self) -> BaseExpression: ...
    def flatten(self) -> Iterator[BaseExpression]: ...
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...

class Expression(_Deconstructible, BaseExpression, Combinable): ...

class CombinedExpression(SQLiteNumericMixin, Expression):
    connector: str
    lhs: Combinable
    rhs: Combinable
    def __init__(self, lhs: Combinable, connector: str, rhs: Combinable, output_field: Field | None = None) -> None: ...

class DurationExpression(CombinedExpression):
    def compile(self, side: Combinable, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...

class TemporalSubtraction(CombinedExpression):
    output_field: ClassVar[fields.DurationField]
    def __init__(self, lhs: Combinable, rhs: Combinable) -> None: ...

class F(_Deconstructible, Combinable):
    name: str
    allowed_default: ClassVar[bool]
    def __init__(self, name: str) -> None: ...
    def __getitem__(self, subscript: int | slice) -> Sliced: ...
    def __contains__(self, other: Any) -> NoReturn: ...
    def resolve_expression(
        self,
        query: Any | None = None,
        allow_joins: bool = True,
        reuse: set[str] | None = None,
        summarize: bool = False,
        for_save: bool = False,
    ) -> Expression: ...
    def replace_expressions(self, replacements: Mapping[F, Any]) -> F: ...
    def asc(
        self,
        *,
        descending: bool = ...,
        nulls_first: bool | None = ...,
        nulls_last: bool | None = ...,
    ) -> OrderBy: ...
    def desc(
        self,
        *,
        nulls_first: bool | None = ...,
        nulls_last: bool | None = ...,
    ) -> OrderBy: ...
    def copy(self) -> Self: ...

class Sliced(F):
    def __init__(self, obj: F, subscript: int | slice) -> None: ...
    def resolve_expression(
        self,
        query: Any | None = None,
        allow_joins: bool = True,
        reuse: set[str] | None = None,
        summarize: bool = False,
        for_save: bool = False,
    ) -> Func: ...

class ResolvedOuterRef(F):
    contains_aggregate: ClassVar[bool]
    contains_over_clause: ClassVar[bool]

class OuterRef(F):
    contains_aggregate: ClassVar[bool]
    contains_over_clause: ClassVar[bool]
    def __init__(self, name: str | OuterRef) -> None: ...
    def relabeled_clone(self, relabels: Any) -> Self: ...

class Func(SQLiteNumericMixin, Expression):
    function: str
    name: str
    template: str
    arg_joiner: str
    arity: int | None
    source_expressions: list[Expression]
    extra: dict[Any, Any]
    def __init__(self, *expressions: Any, output_field: Field | None = None, **extra: Any) -> None: ...
    def as_sql(
        self,
        compiler: SQLCompiler,
        connection: BaseDatabaseWrapper,
        function: str | None = None,
        template: str | None = None,
        arg_joiner: str | None = None,
        **extra_context: Any,
    ) -> _AsSqlType: ...

class Value(Expression):
    value: Any
    def __init__(self, value: Any, output_field: Field | None = None) -> None: ...

class RawSQL(Expression):
    params: list[Any]
    sql: str
    def __init__(self, sql: str, params: Sequence[Any], output_field: Field | None = None) -> None: ...

class Star(Expression): ...

class DatabaseDefault(Expression):
    def __init__(self, expression: Expression, output_field: Field | None = None) -> None: ...

class Col(Expression):
    target: Field
    alias: str
    contains_column_references: Literal[True]
    possibly_multivalued: Literal[False]
    def __init__(self, alias: str, target: Field, output_field: Field | None = None) -> None: ...

class Ref(Expression):
    def __init__(self, refs: str, source: Expression) -> None: ...

class ExpressionList(Func):
    def __init__(
        self, *expressions: BaseExpression | Combinable, output_field: Field | None = None, **extra: Any
    ) -> None: ...

class OrderByList(Func): ...

_E = TypeVar("_E", bound=Q | Combinable)

class ExpressionWrapper(Expression, Generic[_E]):
    def __init__(self, expression: _E, output_field: Field) -> None: ...
    expression: _E

class NegatedExpression(ExpressionWrapper[_E]):
    def __init__(self, expression: _E) -> None: ...
    def __invert__(self) -> _E: ...  # type: ignore[override]

class When(Expression):
    template: str
    condition: Any
    result: Any
    def __init__(self, condition: Any | None = None, then: Any | None = None, **lookups: Any) -> None: ...

class Case(Expression):
    template: str
    case_joiner: str
    cases: Any
    default: Any
    extra: Any
    def __init__(
        self, *cases: Any, default: Any | None = None, output_field: Field | None = None, **extra: Any
    ) -> None: ...

class Subquery(BaseExpression, Combinable):
    template: str
    subquery: bool
    query: Query
    extra: dict[Any, Any]
    def __init__(self, queryset: Query | QuerySet, output_field: Field | None = None, **extra: Any) -> None: ...

class Exists(Subquery):
    output_field: ClassVar[fields.BooleanField]
    def __init__(self, queryset: Query | QuerySet, **kwargs: Any) -> None: ...

class OrderBy(Expression):
    template: str
    nulls_first: bool
    nulls_last: bool
    descending: bool
    expression: Expression | F | Subquery
    def __init__(
        self,
        expression: Expression | F | Subquery,
        descending: bool = False,
        nulls_first: bool | None = None,
        nulls_last: bool | None = None,
    ) -> None: ...
    def asc(self) -> None: ...  # type: ignore[override]
    def desc(self) -> None: ...  # type: ignore[override]

class Window(SQLiteNumericMixin, Expression):
    template: str
    contains_aggregate: bool
    contains_over_clause: bool
    partition_by: ExpressionList | None
    order_by: ExpressionList | None
    def __init__(
        self,
        expression: BaseExpression,
        partition_by: str | Iterable[BaseExpression | F] | F | BaseExpression | None = None,
        order_by: Sequence[BaseExpression | F | str] | BaseExpression | F | str | None = None,
        frame: WindowFrame | None = None,
        output_field: Field | None = None,
    ) -> None: ...

class WindowFrame(Expression):
    template: str
    frame_type: str
    def __init__(self, start: int | None = None, end: int | None = None) -> None: ...
    def window_frame_start_end(
        self, connection: BaseDatabaseWrapper, start: int | None, end: int | None
    ) -> tuple[int, int]: ...

class RowRange(WindowFrame): ...
class ValueRange(WindowFrame): ...

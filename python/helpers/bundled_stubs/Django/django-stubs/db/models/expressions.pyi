import datetime
from collections.abc import Callable, Iterator, Mapping, Sequence
from decimal import Decimal
from enum import Enum
from typing import Any, ClassVar, Generic, Literal, TypeAlias

from django.core.exceptions import FieldError
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models import Q, fields
from django.db.models.fields import Field
from django.db.models.lookups import Lookup, Transform
from django.db.models.query import QuerySet
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType, _ParamsT
from django.db.models.sql.query import Query
from django.utils.deconstruct import _Deconstructible
from django.utils.functional import cached_property
from typing_extensions import Never, Self, TypeVar, override

_OutputField = TypeVar("_OutputField", bound=Field, default=Field)
_Numeric: TypeAlias = float | Decimal
_AddOperand: TypeAlias = datetime.datetime | datetime.timedelta | _Numeric | Combinable | str | None
_SubOperand: TypeAlias = datetime.datetime | datetime.timedelta | _Numeric | Combinable | None
_ExprListCompatible: TypeAlias = Sequence[BaseExpression | F | str] | BaseExpression | F | str

class SQLiteNumericMixin:
    def as_sqlite(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class OutputFieldIsNoneError(FieldError): ...

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
    def __add__(self, other: _AddOperand) -> CombinedExpression: ...
    def __sub__(self, other: _SubOperand) -> CombinedExpression: ...
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
    def __radd__(self, other: _AddOperand) -> CombinedExpression: ...
    def __rsub__(self, other: _SubOperand) -> CombinedExpression: ...
    def __rmul__(self, other: _Numeric | Combinable) -> CombinedExpression: ...
    def __rtruediv__(self, other: _Numeric | Combinable) -> CombinedExpression: ...
    def __rmod__(self, other: int | Combinable) -> CombinedExpression: ...
    def __rpow__(self, other: _Numeric | Combinable) -> CombinedExpression: ...
    def __rand__(self, other: Any) -> Combinable: ...
    def __ror__(self, other: Any) -> Combinable: ...
    def __rxor__(self, other: Any) -> Combinable: ...
    def __invert__(self) -> NegatedExpression[Combinable]: ...

class BaseExpression:
    empty_result_set_value: Any
    is_summary: bool
    filterable: bool
    window_compatible: bool
    allowed_default: bool
    constraint_validation_compatible: bool
    set_returning: bool
    allows_composite_expressions: bool
    def __init__(self, output_field: Field | None = None) -> None: ...
    def get_db_converters(self, connection: BaseDatabaseWrapper) -> list[Callable]: ...
    def get_source_expressions(self) -> list[Any]: ...
    def set_source_expressions(self, exprs: Sequence[Combinable | Expression]) -> None: ...
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...
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
    def replace_expressions(self, replacements: Mapping[Self, Any]) -> Self: ...
    def get_refs(self) -> set[str]: ...
    def copy(self) -> Self: ...
    def prefix_references(self, prefix: str) -> Self: ...
    def get_group_by_cols(self) -> list[BaseExpression]: ...
    def get_source_fields(self) -> list[Field | None]: ...
    def asc(self, **kwargs: Any) -> OrderBy: ...
    def desc(self, **kwargs: Any) -> OrderBy: ...
    def reverse_ordering(self) -> BaseExpression: ...
    def flatten(self) -> Iterator[BaseExpression]: ...
    def select_format(self, compiler: SQLCompiler, sql: str, params: _ParamsT) -> _AsSqlType: ...
    def get_expression_for_validation(self) -> BaseExpression: ...

class Expression(_Deconstructible, BaseExpression, Combinable):
    @cached_property
    def identity(self) -> tuple[Any, ...]: ...

def register_combinable_fields(
    lhs: type[Field],
    connector: str,
    rhs: type[Field],
    result: type[Field],
) -> None: ...

class CombinedExpression(SQLiteNumericMixin, Expression):
    @cached_property
    @override
    def allowed_default(self) -> bool: ...  # type: ignore[override]
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
    allowed_default: ClassVar[bool]
    def __init__(self, name: str) -> None: ...
    def __getitem__(self, subscript: int | slice) -> Sliced: ...
    def __contains__(self, other: Any) -> Never: ...
    def resolve_expression(
        self,
        query: Any | None = None,
        allow_joins: bool = True,
        reuse: set[str] | None = None,
        summarize: bool = False,
        for_save: bool = False,
    ) -> Expression: ...
    def replace_expressions(self, replacements: Mapping[F, Any]) -> F: ...
    def asc(self, **kwargs: Any) -> OrderBy: ...
    def desc(self, **kwargs: Any) -> OrderBy: ...
    def copy(self) -> Self: ...

class Sliced(F):
    def __init__(self, obj: F, subscript: int | slice) -> None: ...
    @override
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
    def as_sql(self, *args: Any, **kwargs: Any) -> _AsSqlType: ...
    def relabeled_clone(self, relabels: Mapping[str, str]) -> Self: ...
    def get_group_by_cols(self) -> list[BaseExpression]: ...

class OuterRef(F):
    contains_aggregate: ClassVar[bool]
    contains_over_clause: ClassVar[bool]
    def __init__(self, name: str | OuterRef) -> None: ...
    def relabeled_clone(self, relabels: Any) -> Self: ...

class Func(SQLiteNumericMixin, Expression, Generic[_OutputField]):
    @cached_property
    @override
    def allowed_default(self) -> bool: ...  # type: ignore[override]
    function: str | None = None
    template: str
    arg_joiner: str
    arity: int | None
    source_expressions: list[Expression]
    extra: dict[Any, Any]
    def __init__(self, *expressions: Any, output_field: _OutputField | None = None, **extra: Any) -> None: ...
    @override
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
    for_save: bool
    def __init__(self, value: Any, output_field: Field | None = None) -> None: ...
    @property
    @override
    def empty_result_set_value(self) -> Any: ...

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
    @override
    def relabeled_clone(self, relabels: Mapping[str, str]) -> Self: ...

class ColPairs(Expression):
    alias: str
    targets: Sequence[Field]
    sources: Sequence[Field]
    def __init__(
        self, alias: str, targets: Sequence[Field], sources: Sequence[Field], output_field: Field | None
    ) -> None: ...
    def __len__(self) -> int: ...
    def __iter__(self) -> Iterator[Col]: ...
    def get_cols(self) -> list[Col]: ...
    @override
    def relabeled_clone(self, relabels: Mapping[str, str]) -> Self: ...

class Ref(Expression):
    def __init__(self, refs: str, source: Expression) -> None: ...
    @override
    def relabeled_clone(self, relabels: Mapping[str, str]) -> Self: ...

class ExpressionList(Func):
    def __init__(
        self, *expressions: BaseExpression | Combinable, output_field: Field | None = None, **extra: Any
    ) -> None: ...

class OrderByList(ExpressionList):
    @classmethod
    def from_param(cls, context: str, param: _ExprListCompatible | None) -> Self | None: ...

_E = TypeVar("_E", bound=Q | Combinable)

class ExpressionWrapper(Expression, Generic[_E]):
    @property
    @override
    def allowed_default(self) -> bool: ...  # type: ignore[override]
    def __init__(self, expression: _E, output_field: Field) -> None: ...
    expression: _E

class NegatedExpression(ExpressionWrapper[_E]):
    def __init__(self, expression: _E) -> None: ...
    @override
    def __invert__(self) -> _E: ...  # type: ignore[override]

class When(Expression):
    @cached_property
    @override
    def allowed_default(self) -> bool: ...  # type: ignore[override]
    template: str
    condition: Any
    result: Any
    def __init__(self, condition: Any | None = None, then: Any | None = None, **lookups: Any) -> None: ...
    @override
    def as_sql(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, template: str | None = None, **extra_context: Any
    ) -> _AsSqlType: ...

class Case(Expression):
    @cached_property
    @override
    def allowed_default(self) -> bool: ...  # type: ignore[override]
    template: str
    case_joiner: str
    cases: Any
    default: Any
    extra: Any
    def __init__(
        self, *cases: Any, default: Any | None = None, output_field: Field | None = None, **extra: Any
    ) -> None: ...
    @override
    def as_sql(
        self,
        compiler: SQLCompiler,
        connection: BaseDatabaseWrapper,
        template: str | None = None,
        case_joiner: str | None = None,
        **extra_context: Any,
    ) -> _AsSqlType: ...

class Subquery(BaseExpression, Combinable, Generic[_OutputField]):
    template: str
    subquery: bool
    query: Query
    extra: dict[Any, Any]
    def __init__(
        self, queryset: Query | QuerySet | Subquery, output_field: _OutputField | None = None, **extra: Any
    ) -> None: ...
    @property
    def external_aliases(self) -> set[str]: ...
    def get_external_cols(self) -> list[Col]: ...
    @override
    def as_sql(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, template: str | None = None, **extra_context: Any
    ) -> _AsSqlType: ...

class Exists(Subquery):
    output_field: ClassVar[fields.BooleanField]
    def __init__(self, queryset: Query | QuerySet | Subquery, **kwargs: Any) -> None: ...

class OrderBy(Expression):
    template: str
    nulls_first: bool
    nulls_last: bool
    descending: bool
    expression: Expression | F | Subquery
    allows_composite_expressions: bool
    def __init__(
        self,
        expression: Expression | F | Subquery,
        descending: bool = False,
        nulls_first: bool | None = None,
        nulls_last: bool | None = None,
    ) -> None: ...
    @override
    def as_sql(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, template: str | None = None, **extra_context: Any
    ) -> _AsSqlType: ...
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...
    @override
    def asc(self) -> None: ...  # type: ignore[override]
    @override
    def desc(self) -> None: ...  # type: ignore[override]

class Window(SQLiteNumericMixin, Expression):
    template: str
    contains_aggregate: bool
    contains_over_clause: bool
    partition_by: ExpressionList | None
    order_by: OrderByList | None
    def __init__(
        self,
        expression: BaseExpression,
        partition_by: _ExprListCompatible | None = None,
        order_by: _ExprListCompatible | None = None,
        frame: WindowFrame | None = None,
        output_field: Field | None = None,
    ) -> None: ...
    @override
    def as_sql(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, template: str | None = None
    ) -> _AsSqlType: ...
    @override
    def as_sqlite(  # type: ignore[override]
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper
    ) -> _AsSqlType: ...

class WindowFrameExclusion(Enum):
    CURRENT_ROW = "CURRENT ROW"
    GROUP = "GROUP"
    TIES = "TIES"
    NO_OTHERS = "NO OTHERS"

class WindowFrame(Expression):
    template: str
    def __init__(
        self,
        start: int | None = None,
        end: int | None = None,
        exclusion: WindowFrameExclusion | None = None,
    ) -> None: ...
    def get_exclusion(self) -> str: ...
    def window_frame_start_end(
        self, connection: BaseDatabaseWrapper, start: int | None, end: int | None
    ) -> tuple[int, int]: ...

class RowRange(WindowFrame):
    frame_type: str

class ValueRange(WindowFrame):
    frame_type: str

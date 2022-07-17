from typing import Any, Generic, TypeVar
from typing_extensions import Literal

from .. import util
from ..util import HasMemoized, memoized_property
from . import operators, roles
from .annotation import Annotated, SupportsWrappingAnnotations
from .base import Executable, Immutable, SingletonConstant
from .traversals import HasCopyInternals, MemoizedHasCacheKey
from .visitors import Traversible

_T = TypeVar("_T")

def collate(expression, collation): ...
def between(expr, lower_bound, upper_bound, symmetric: bool = ...): ...
def literal(value, type_: Any | None = ...): ...
def outparam(key, type_: Any | None = ...): ...
def not_(clause): ...

class ClauseElement(roles.SQLRole, SupportsWrappingAnnotations, MemoizedHasCacheKey, HasCopyInternals, Traversible):
    __visit_name__: str
    supports_execution: bool
    stringify_dialect: str
    bind: Any
    description: Any
    is_clause_element: bool
    is_selectable: bool
    @property
    def entity_namespace(self) -> None: ...
    def unique_params(self, *optionaldict, **kwargs): ...
    def params(self, *optionaldict, **kwargs): ...
    def compare(self, other, **kw): ...
    def self_group(self, against: Any | None = ...): ...
    def compile(self, bind: Any | None = ..., dialect: Any | None = ..., **kw): ...
    def __invert__(self): ...
    def __bool__(self) -> None: ...
    __nonzero__: Any

class ColumnElement(
    roles.ColumnArgumentOrKeyRole,
    roles.StatementOptionRole,
    roles.WhereHavingRole,
    roles.BinaryElementRole,
    roles.OrderByRole,
    roles.ColumnsClauseRole,
    roles.LimitOffsetRole,
    roles.DMLColumnRole,
    roles.DDLConstraintColumnRole,
    roles.DDLExpressionRole,
    operators.ColumnOperators[_T],
    ClauseElement,
    Generic[_T],
):
    __visit_name__: str
    primary_key: bool
    foreign_keys: Any
    key: Any
    def self_group(self, against: Any | None = ...): ...
    @memoized_property
    def type(self): ...
    @HasMemoized.memoized_attribute
    def comparator(self): ...
    def __getattr__(self, key): ...
    def operate(self, op, *other, **kwargs): ...
    def reverse_operate(self, op, other, **kwargs): ...
    @property
    def expression(self): ...
    @memoized_property
    def base_columns(self): ...
    @memoized_property
    def proxy_set(self): ...
    def shares_lineage(self, othercolumn): ...
    def cast(self, type_): ...
    def label(self, name): ...
    @property
    def anon_label(self): ...
    @property
    def anon_key_label(self): ...

class WrapsColumnExpression:
    @property
    def wrapped_column_expression(self) -> None: ...

class BindParameter(roles.InElementRole, ColumnElement[_T], Generic[_T]):
    __visit_name__: str
    inherit_cache: bool
    key: Any
    unique: Any
    value: Any
    callable: Any
    isoutparam: Any
    required: Any
    expanding: Any
    expand_op: Any
    literal_execute: Any
    type: Any
    def __init__(
        self,
        key,
        value=...,
        type_: Any | None = ...,
        unique: bool = ...,
        required=...,
        quote: Any | None = ...,
        callable_: Any | None = ...,
        expanding: bool = ...,
        isoutparam: bool = ...,
        literal_execute: bool = ...,
        _compared_to_operator: Any | None = ...,
        _compared_to_type: Any | None = ...,
        _is_crud: bool = ...,
    ) -> None: ...
    @property
    def effective_value(self): ...
    def render_literal_execute(self): ...

class TypeClause(ClauseElement):
    __visit_name__: str
    type: Any
    def __init__(self, type_) -> None: ...

class TextClause(
    roles.DDLConstraintColumnRole,
    roles.DDLExpressionRole,
    roles.StatementOptionRole,
    roles.WhereHavingRole,
    roles.OrderByRole,
    roles.FromClauseRole,
    roles.SelectStatementRole,
    roles.BinaryElementRole,
    roles.InElementRole,
    Executable,
    ClauseElement,
):
    __visit_name__: str
    def __and__(self, other): ...
    key: Any
    text: Any
    def __init__(self, text, bind: Any | None = ...): ...
    def bindparams(self, *binds, **names_to_values) -> None: ...
    def columns(self, *cols, **types): ...
    @property
    def type(self): ...
    @property
    def comparator(self): ...
    def self_group(self, against: Any | None = ...): ...

class Null(SingletonConstant, roles.ConstExprRole, ColumnElement[None]):
    __visit_name__: str
    @memoized_property
    def type(self): ...

class False_(SingletonConstant, roles.ConstExprRole, ColumnElement[Literal[False]]):
    __visit_name__: str
    @memoized_property
    def type(self): ...

class True_(SingletonConstant, roles.ConstExprRole, ColumnElement[Literal[True]]):
    __visit_name__: str
    @memoized_property
    def type(self): ...

class ClauseList(roles.InElementRole, roles.OrderByRole, roles.ColumnsClauseRole, roles.DMLColumnRole, ClauseElement):
    __visit_name__: str
    operator: Any
    group: Any
    group_contents: Any
    clauses: Any
    def __init__(self, *clauses, **kwargs) -> None: ...
    def __iter__(self): ...
    def __len__(self): ...
    def append(self, clause) -> None: ...
    def self_group(self, against: Any | None = ...): ...

class BooleanClauseList(ClauseList, ColumnElement[Any]):
    __visit_name__: str
    inherit_cache: bool
    def __init__(self, *arg, **kw) -> None: ...
    @classmethod
    def and_(cls, *clauses): ...
    @classmethod
    def or_(cls, *clauses): ...
    def self_group(self, against: Any | None = ...): ...

and_: Any
or_: Any

class Tuple(ClauseList, ColumnElement[Any]):
    __visit_name__: str
    type: Any
    def __init__(self, *clauses, **kw) -> None: ...
    def self_group(self, against: Any | None = ...): ...

class Case(ColumnElement[Any]):
    __visit_name__: str
    value: Any
    type: Any
    whens: Any
    else_: Any
    def __init__(self, *whens, **kw) -> None: ...

def literal_column(text, type_: Any | None = ...): ...

class Cast(WrapsColumnExpression, ColumnElement[Any]):
    __visit_name__: str
    type: Any
    clause: Any
    typeclause: Any
    def __init__(self, expression, type_) -> None: ...
    @property
    def wrapped_column_expression(self): ...

class TypeCoerce(WrapsColumnExpression, ColumnElement[Any]):
    __visit_name__: str
    type: Any
    clause: Any
    def __init__(self, expression, type_) -> None: ...
    @HasMemoized.memoized_attribute
    def typed_expression(self): ...
    @property
    def wrapped_column_expression(self): ...
    def self_group(self, against: Any | None = ...): ...

class Extract(ColumnElement[Any]):
    __visit_name__: str
    type: Any
    field: Any
    expr: Any
    def __init__(self, field, expr, **kwargs) -> None: ...

class _label_reference(ColumnElement[Any]):
    __visit_name__: str
    element: Any
    def __init__(self, element) -> None: ...

class _textual_label_reference(ColumnElement[Any]):
    __visit_name__: str
    element: Any
    def __init__(self, element) -> None: ...

class UnaryExpression(ColumnElement[Any]):
    __visit_name__: str
    operator: Any
    modifier: Any
    element: Any
    type: Any
    wraps_column_expression: Any
    def __init__(
        self,
        element,
        operator: Any | None = ...,
        modifier: Any | None = ...,
        type_: Any | None = ...,
        wraps_column_expression: bool = ...,
    ) -> None: ...
    def self_group(self, against: Any | None = ...): ...

class CollectionAggregate(UnaryExpression):
    inherit_cache: bool
    def operate(self, op, *other, **kwargs): ...
    def reverse_operate(self, op, other, **kwargs) -> None: ...

class AsBoolean(WrapsColumnExpression, UnaryExpression):
    inherit_cache: bool
    element: Any
    type: Any
    operator: Any
    negate: Any
    modifier: Any
    wraps_column_expression: bool
    def __init__(self, element, operator, negate) -> None: ...
    @property
    def wrapped_column_expression(self): ...
    def self_group(self, against: Any | None = ...): ...

class BinaryExpression(ColumnElement[Any]):
    __visit_name__: str
    left: Any
    right: Any
    operator: Any
    type: Any
    negate: Any
    modifiers: Any
    def __init__(
        self, left, right, operator, type_: Any | None = ..., negate: Any | None = ..., modifiers: Any | None = ...
    ) -> None: ...
    def __bool__(self): ...
    __nonzero__: Any
    @property
    def is_comparison(self): ...
    def self_group(self, against: Any | None = ...): ...

class Slice(ColumnElement[Any]):
    __visit_name__: str
    start: Any
    stop: Any
    step: Any
    type: Any
    def __init__(self, start, stop, step, _name: Any | None = ...) -> None: ...
    def self_group(self, against: Any | None = ...): ...

class IndexExpression(BinaryExpression):
    inherit_cache: bool

class GroupedElement(ClauseElement):
    __visit_name__: str
    def self_group(self, against: Any | None = ...): ...

class Grouping(GroupedElement, ColumnElement[Any]):
    element: Any
    type: Any
    def __init__(self, element) -> None: ...
    def __getattr__(self, attr): ...

RANGE_UNBOUNDED: Any
RANGE_CURRENT: Any

class Over(ColumnElement[Any]):
    __visit_name__: str
    order_by: Any
    partition_by: Any
    element: Any
    range_: Any
    rows: Any
    def __init__(
        self,
        element,
        partition_by: Any | None = ...,
        order_by: Any | None = ...,
        range_: Any | None = ...,
        rows: Any | None = ...,
    ) -> None: ...
    def __reduce__(self): ...
    @memoized_property
    def type(self): ...

class WithinGroup(ColumnElement[Any]):
    __visit_name__: str
    order_by: Any
    element: Any
    def __init__(self, element, *order_by) -> None: ...
    def __reduce__(self): ...
    def over(
        self, partition_by: Any | None = ..., order_by: Any | None = ..., range_: Any | None = ..., rows: Any | None = ...
    ): ...
    @memoized_property
    def type(self): ...

class FunctionFilter(ColumnElement[Any]):
    __visit_name__: str
    criterion: Any
    func: Any
    def __init__(self, func, *criterion) -> None: ...
    def filter(self, *criterion): ...
    def over(
        self, partition_by: Any | None = ..., order_by: Any | None = ..., range_: Any | None = ..., rows: Any | None = ...
    ): ...
    def self_group(self, against: Any | None = ...): ...
    @memoized_property
    def type(self): ...

class Label(roles.LabeledColumnExprRole, ColumnElement[Any]):
    __visit_name__: str
    name: Any
    key: Any
    def __init__(self, name, element, type_: Any | None = ...) -> None: ...
    def __reduce__(self): ...
    @memoized_property
    def type(self): ...
    @HasMemoized.memoized_attribute
    def element(self): ...
    def self_group(self, against: Any | None = ...): ...
    @property
    def primary_key(self): ...
    @property
    def foreign_keys(self): ...

class NamedColumn(ColumnElement[Any]):
    is_literal: bool
    table: Any
    @memoized_property
    def description(self): ...

class ColumnClause(roles.DDLReferredColumnRole, roles.LabeledColumnExprRole, roles.StrAsPlainColumnRole, Immutable, NamedColumn):
    table: Any
    is_literal: bool
    __visit_name__: str
    onupdate: Any
    default: Any
    server_default: Any
    server_onupdate: Any
    key: Any
    type: Any
    def __init__(self, text, type_: Any | None = ..., is_literal: bool = ..., _selectable: Any | None = ...) -> None: ...
    def get_children(self, column_tables: bool = ..., **kw): ...  # type: ignore[override]
    @property
    def entity_namespace(self): ...

class TableValuedColumn(NamedColumn):
    __visit_name__: str
    scalar_alias: Any
    key: Any
    type: Any
    def __init__(self, scalar_alias, type_) -> None: ...

class CollationClause(ColumnElement[Any]):
    __visit_name__: str
    collation: Any
    def __init__(self, collation) -> None: ...

class _IdentifiedClause(Executable, ClauseElement):
    __visit_name__: str
    ident: Any
    def __init__(self, ident) -> None: ...

class SavepointClause(_IdentifiedClause):
    __visit_name__: str
    inherit_cache: bool

class RollbackToSavepointClause(_IdentifiedClause):
    __visit_name__: str
    inherit_cache: bool

class ReleaseSavepointClause(_IdentifiedClause):
    __visit_name__: str
    inherit_cache: bool

class quoted_name(util.MemoizedSlots, util.text_type):
    quote: Any
    def __new__(cls, value, quote): ...
    def __reduce__(self): ...

class AnnotatedColumnElement(Annotated):
    def __init__(self, element, values) -> None: ...
    @memoized_property
    def name(self): ...
    @memoized_property
    def table(self): ...
    @memoized_property
    def key(self): ...
    @memoized_property
    def info(self): ...

class _truncated_label(quoted_name):
    def __new__(cls, value, quote: Any | None = ...): ...
    def __reduce__(self): ...
    def apply_map(self, map_): ...

class conv(_truncated_label): ...

class _anonymous_label(_truncated_label):
    @classmethod
    def safe_construct(cls, seed, body, enclosing_label: Any | None = ..., sanitize_key: bool = ...): ...
    def __add__(self, other): ...
    def __radd__(self, other): ...
    def apply_map(self, map_): ...

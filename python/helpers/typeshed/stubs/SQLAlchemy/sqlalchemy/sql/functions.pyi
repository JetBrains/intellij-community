from typing import Any

from ..util import HasMemoized
from .base import Executable, Generative
from .elements import BinaryExpression, ColumnElement, NamedColumn
from .selectable import FromClause, TableValuedAlias
from .visitors import TraversibleType

def register_function(identifier, fn, package: str = ...) -> None: ...

class FunctionElement(Executable, ColumnElement[Any], FromClause, Generative):  # type: ignore[misc]
    packagenames: Any
    clause_expr: Any
    def __init__(self, *clauses, **kwargs) -> None: ...
    def scalar_table_valued(self, name, type_: Any | None = ...): ...
    def table_valued(self, *expr, **kw): ...
    def column_valued(self, name: Any | None = ...): ...
    @property
    def columns(self): ...
    @property
    def exported_columns(self): ...
    @HasMemoized.memoized_attribute
    def clauses(self): ...
    def over(
        self, partition_by: Any | None = ..., order_by: Any | None = ..., rows: Any | None = ..., range_: Any | None = ...
    ): ...
    def within_group(self, *order_by): ...
    def filter(self, *criterion): ...
    def as_comparison(self, left_index, right_index): ...
    def within_group_type(self, within_group) -> None: ...
    def alias(self, name: str | None = ..., joins_implicitly: bool = ...) -> TableValuedAlias: ...  # type: ignore[override]
    def select(self): ...
    def scalar(self): ...
    def execute(self): ...
    def self_group(self, against: Any | None = ...): ...
    @property
    def entity_namespace(self): ...

class FunctionAsBinary(BinaryExpression):
    sql_function: Any
    left_index: Any
    right_index: Any
    operator: Any
    type: Any
    negate: Any
    modifiers: Any
    def __init__(self, fn, left_index, right_index) -> None: ...
    @property
    def left(self): ...
    @left.setter
    def left(self, value) -> None: ...
    @property
    def right(self): ...
    @right.setter
    def right(self, value) -> None: ...

class ScalarFunctionColumn(NamedColumn):
    __visit_name__: str
    is_literal: bool
    table: Any
    fn: Any
    name: Any
    type: Any
    def __init__(self, fn, name, type_: Any | None = ...) -> None: ...

class _FunctionGenerator:
    opts: Any
    def __init__(self, **opts) -> None: ...
    def __getattr__(self, name): ...
    def __call__(self, *c, **kwargs): ...

func: Any
modifier: Any

class Function(FunctionElement):
    __visit_name__: str
    type: Any
    packagenames: Any
    name: Any
    def __init__(self, name, *clauses, **kw) -> None: ...

class _GenericMeta(TraversibleType):
    def __init__(cls, clsname, bases, clsdict) -> None: ...

class GenericFunction:
    name: Any
    identifier: Any
    coerce_arguments: bool
    inherit_cache: bool
    packagenames: Any
    clause_expr: Any
    type: Any
    def __init__(self, *args, **kwargs) -> None: ...

class next_value(GenericFunction):
    type: Any
    name: str
    sequence: Any
    def __init__(self, seq, **kw) -> None: ...
    def compare(self, other, **kw): ...

class AnsiFunction(GenericFunction):
    inherit_cache: bool
    def __init__(self, *args, **kwargs) -> None: ...

class ReturnTypeFromArgs(GenericFunction):
    inherit_cache: bool
    def __init__(self, *args, **kwargs) -> None: ...

class coalesce(ReturnTypeFromArgs):
    inherit_cache: bool

class max(ReturnTypeFromArgs):
    inherit_cache: bool

class min(ReturnTypeFromArgs):
    inherit_cache: bool

class sum(ReturnTypeFromArgs):
    inherit_cache: bool

class now(GenericFunction):
    type: Any
    inherit_cache: bool

class concat(GenericFunction):
    type: Any
    inherit_cache: bool

class char_length(GenericFunction):
    type: Any
    inherit_cache: bool
    def __init__(self, arg, **kwargs) -> None: ...

class random(GenericFunction):
    inherit_cache: bool

class count(GenericFunction):
    type: Any
    inherit_cache: bool
    def __init__(self, expression: Any | None = ..., **kwargs) -> None: ...

class current_date(AnsiFunction):
    type: Any
    inherit_cache: bool

class current_time(AnsiFunction):
    type: Any
    inherit_cache: bool

class current_timestamp(AnsiFunction):
    type: Any
    inherit_cache: bool

class current_user(AnsiFunction):
    type: Any
    inherit_cache: bool

class localtime(AnsiFunction):
    type: Any
    inherit_cache: bool

class localtimestamp(AnsiFunction):
    type: Any
    inherit_cache: bool

class session_user(AnsiFunction):
    type: Any
    inherit_cache: bool

class sysdate(AnsiFunction):
    type: Any
    inherit_cache: bool

class user(AnsiFunction):
    type: Any
    inherit_cache: bool

class array_agg(GenericFunction):
    type: Any
    inherit_cache: bool
    def __init__(self, *args, **kwargs) -> None: ...

class OrderedSetAgg(GenericFunction):
    array_for_multi_clause: bool
    inherit_cache: bool
    def within_group_type(self, within_group): ...

class mode(OrderedSetAgg):
    inherit_cache: bool

class percentile_cont(OrderedSetAgg):
    array_for_multi_clause: bool
    inherit_cache: bool

class percentile_disc(OrderedSetAgg):
    array_for_multi_clause: bool
    inherit_cache: bool

class rank(GenericFunction):
    type: Any
    inherit_cache: bool

class dense_rank(GenericFunction):
    type: Any
    inherit_cache: bool

class percent_rank(GenericFunction):
    type: Any
    inherit_cache: bool

class cume_dist(GenericFunction):
    type: Any
    inherit_cache: bool

class cube(GenericFunction):
    inherit_cache: bool

class rollup(GenericFunction):
    inherit_cache: bool

class grouping_sets(GenericFunction):
    inherit_cache: bool

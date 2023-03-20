from typing import Any, Generic, TypeVar

from . import elements, roles
from .base import Options
from .operators import ColumnOperators

_T = TypeVar("_T")

class LambdaOptions(Options):
    enable_tracking: bool
    track_closure_variables: bool
    track_on: Any
    global_track_bound_values: bool
    track_bound_values: bool
    lambda_cache: Any

def lambda_stmt(
    lmb,
    enable_tracking: bool = ...,
    track_closure_variables: bool = ...,
    track_on: Any | None = ...,
    global_track_bound_values: bool = ...,
    track_bound_values: bool = ...,
    lambda_cache: Any | None = ...,
): ...

class LambdaElement(elements.ClauseElement):
    __visit_name__: str
    parent_lambda: Any
    fn: Any
    role: Any
    tracker_key: Any
    opts: Any
    def __init__(self, fn, role, opts=..., apply_propagate_attrs: Any | None = ...) -> None: ...
    def __getattr__(self, key): ...

class DeferredLambdaElement(LambdaElement):
    lambda_args: Any
    def __init__(self, fn, role, opts=..., lambda_args=...) -> None: ...

class StatementLambdaElement(roles.AllowsLambdaRole, LambdaElement):
    def __add__(self, other): ...
    def add_criteria(
        self,
        other,
        enable_tracking: bool = ...,
        track_on: Any | None = ...,
        track_closure_variables: bool = ...,
        track_bound_values: bool = ...,
    ): ...
    def spoil(self): ...

class NullLambdaStatement(roles.AllowsLambdaRole, elements.ClauseElement):
    __visit_name__: str
    def __init__(self, statement) -> None: ...
    def __getattr__(self, key): ...
    def __add__(self, other): ...
    def add_criteria(self, other, **kw): ...

class LinkedLambdaElement(StatementLambdaElement):
    role: Any
    opts: Any
    fn: Any
    parent_lambda: Any
    tracker_key: Any
    def __init__(self, fn, parent_lambda, opts) -> None: ...

class AnalyzedCode:
    @classmethod
    def get(cls, fn, lambda_element, lambda_kw, **kw): ...
    track_bound_values: Any
    track_closure_variables: Any
    bindparam_trackers: Any
    closure_trackers: Any
    build_py_wrappers: Any
    def __init__(self, fn, lambda_element, opts) -> None: ...

class NonAnalyzedFunction:
    closure_bindparams: Any
    bindparam_trackers: Any
    expr: Any
    def __init__(self, expr) -> None: ...
    @property
    def expected_expr(self): ...

class AnalyzedFunction:
    analyzed_code: Any
    fn: Any
    closure_pywrappers: Any
    tracker_instrumented_fn: Any
    expr: Any
    bindparam_trackers: Any
    expected_expr: Any
    is_sequence: Any
    propagate_attrs: Any
    closure_bindparams: Any
    def __init__(self, analyzed_code, lambda_element, apply_propagate_attrs, fn) -> None: ...

class PyWrapper(ColumnOperators[_T], Generic[_T]):
    fn: Any
    track_bound_values: Any
    def __init__(
        self, fn, name, to_evaluate, closure_index: Any | None = ..., getter: Any | None = ..., track_bound_values: bool = ...
    ) -> None: ...
    def __call__(self, *arg, **kw): ...
    def operate(self, op, *other, **kwargs): ...
    def reverse_operate(self, op, other, **kwargs): ...
    def __clause_element__(self): ...
    def __bool__(self): ...
    def __nonzero__(self): ...
    def __getattribute__(self, key): ...
    def __iter__(self): ...
    def __getitem__(self, key) -> ColumnOperators[_T]: ...

def insp(lmb): ...

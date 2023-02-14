from typing import Any

from . import visitors

join_condition: Any

def find_join_source(clauses, join_to): ...
def find_left_clause_that_matches_given(clauses, join_from): ...
def find_left_clause_to_join_from(clauses, join_to, onclause): ...
def visit_binary_product(fn, expr) -> None: ...
def find_tables(
    clause,
    check_columns: bool = ...,
    include_aliases: bool = ...,
    include_joins: bool = ...,
    include_selects: bool = ...,
    include_crud: bool = ...,
): ...
def unwrap_order_by(clause): ...
def unwrap_label_reference(element): ...
def expand_column_list_from_order_by(collist, order_by): ...
def clause_is_present(clause, search): ...
def tables_from_leftmost(clause) -> None: ...
def surface_selectables(clause) -> None: ...
def surface_selectables_only(clause) -> None: ...
def extract_first_column_annotation(column, annotation_name): ...
def selectables_overlap(left, right): ...
def bind_values(clause): ...

class _repr_base:
    max_chars: Any
    def trunc(self, value): ...

class _repr_row(_repr_base):
    row: Any
    max_chars: Any
    def __init__(self, row, max_chars: int = ...) -> None: ...

class _repr_params(_repr_base):
    params: Any
    ismulti: Any
    batches: Any
    max_chars: Any
    def __init__(self, params, batches, max_chars: int = ..., ismulti: Any | None = ...) -> None: ...

def adapt_criterion_to_null(crit, nulls): ...
def splice_joins(left, right, stop_on: Any | None = ...): ...
def reduce_columns(columns, *clauses, **kw): ...
def criterion_as_pairs(
    expression,
    consider_as_foreign_keys: Any | None = ...,
    consider_as_referenced_keys: Any | None = ...,
    any_operator: bool = ...,
): ...

class ClauseAdapter(visitors.ReplacingExternalTraversal):
    __traverse_options__: Any
    selectable: Any
    include_fn: Any
    exclude_fn: Any
    equivalents: Any
    adapt_on_names: Any
    adapt_from_selectables: Any
    def __init__(
        self,
        selectable,
        equivalents: Any | None = ...,
        include_fn: Any | None = ...,
        exclude_fn: Any | None = ...,
        adapt_on_names: bool = ...,
        anonymize_labels: bool = ...,
        adapt_from_selectables: Any | None = ...,
    ) -> None: ...
    def replace(self, col, _include_singleton_constants: bool = ...): ...

class ColumnAdapter(ClauseAdapter):
    columns: Any
    adapt_required: Any
    allow_label_resolve: Any
    def __init__(
        self,
        selectable,
        equivalents: Any | None = ...,
        adapt_required: bool = ...,
        include_fn: Any | None = ...,
        exclude_fn: Any | None = ...,
        adapt_on_names: bool = ...,
        allow_label_resolve: bool = ...,
        anonymize_labels: bool = ...,
        adapt_from_selectables: Any | None = ...,
    ) -> None: ...

    class _IncludeExcludeMapping:
        parent: Any
        columns: Any
        def __init__(self, parent, columns) -> None: ...
        def __getitem__(self, key): ...

    def wrap(self, adapter): ...
    def traverse(self, obj): ...
    adapt_clause: Any
    adapt_list: Any
    def adapt_check_present(self, col): ...

from collections.abc import Callable, Iterable, Iterator, Sequence
from datetime import date, datetime
from decimal import Decimal
from typing import Any, Literal, overload
from uuid import UUID

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.backends.utils import CursorWrapper
from django.db.models.base import Model
from django.db.models.expressions import BaseExpression, Expression, Ref
from django.db.models.sql.query import Query
from django.db.models.sql.subqueries import AggregateQuery, DeleteQuery, InsertQuery, UpdateQuery
from django.utils.functional import cached_property
from typing_extensions import TypeAlias

_ParamT: TypeAlias = str | int

_ParamsT: TypeAlias = list[_ParamT]
_AsSqlType: TypeAlias = tuple[str, _ParamsT]

class PositionRef(Ref):
    def __init__(self, ordinal: str, refs: str, source: Expression) -> None: ...

class SQLCompiler:
    query: Query
    connection: BaseDatabaseWrapper
    using: str | None
    quote_cache: Any
    select: Any
    annotation_col_map: Any
    klass_info: Any
    ordering_parts: Any
    def __init__(self, query: Query, connection: BaseDatabaseWrapper, using: str | None) -> None: ...
    col_count: int | None
    def setup_query(self, with_col_aliases: bool = False) -> None: ...
    has_extra_select: Any
    def pre_sql_setup(self, with_col_aliases: bool = False) -> tuple[
        list[tuple[Expression, _AsSqlType, None]],
        list[tuple[Expression, tuple[str, _ParamsT, bool]]],
        list[_AsSqlType],
    ]: ...
    def get_group_by(
        self,
        select: list[tuple[BaseExpression, _AsSqlType, str | None]],
        order_by: list[tuple[Expression, tuple[str, _ParamsT, bool]]],
    ) -> list[_AsSqlType]: ...
    def collapse_group_by(
        self, expressions: list[Expression], having: list[Expression] | tuple
    ) -> list[Expression]: ...
    def get_select(
        self, with_col_aliases: bool = False
    ) -> tuple[list[tuple[Expression, _AsSqlType, str | None]], dict[str, Any] | None, dict[str, int]]: ...
    def _order_by_pairs(self) -> None: ...
    def get_order_by(self) -> list[tuple[Expression, tuple[str, _ParamsT, bool]]]: ...
    def get_extra_select(
        self,
        order_by: list[tuple[Expression, tuple[str, _ParamsT, bool]]],
        select: list[tuple[Expression, _AsSqlType, str | None]],
    ) -> list[tuple[Expression, _AsSqlType, None]]: ...
    def quote_name_unless_alias(self, name: str) -> str: ...
    def compile(self, node: BaseExpression) -> _AsSqlType: ...
    def get_combinator_sql(self, combinator: str, all: bool) -> tuple[list[str], list[int] | list[str]]: ...
    def as_sql(self, with_limits: bool = True, with_col_aliases: bool = False) -> _AsSqlType: ...
    def get_default_columns(
        self, start_alias: str | None = None, opts: Any | None = None, from_parent: type[Model] | None = None
    ) -> list[Expression]: ...
    def get_distinct(self) -> tuple[list[Any], list[Any]]: ...
    def find_ordering_name(
        self,
        name: str,
        opts: Any,
        alias: str | None = None,
        default_order: str = "ASC",
        already_seen: set[tuple[tuple[tuple[str, str]] | None, tuple[tuple[str, str]]]] | None = None,
    ) -> list[tuple[Expression, bool]]: ...
    def get_from_clause(self) -> tuple[list[str], _ParamsT]: ...
    def get_related_selections(
        self,
        select: list[tuple[Expression, str | None]],
        opts: Any | None = None,
        root_alias: str | None = None,
        cur_depth: int = 1,
        requested: dict[str, dict[str, dict[str, dict[Any, Any]]]] | None = None,
        restricted: bool | None = None,
    ) -> list[dict[str, Any]]: ...
    def get_select_for_update_of_arguments(self) -> list[Any]: ...
    def deferred_to_columns(self) -> dict[type[Model], set[str]]: ...
    def get_converters(self, expressions: list[Expression]) -> dict[int, tuple[list[Callable], Expression]]: ...
    def apply_converters(
        self, rows: Iterable[Iterable[Any]], converters: dict[int, tuple[list[Callable], Expression]]
    ) -> Iterator[list[None | date | datetime | float | Decimal | UUID | bytes | str]]: ...
    def results_iter(
        self,
        results: Iterable[list[Sequence[Any]]] | None = None,
        tuple_expected: bool = False,
        chunked_fetch: bool = False,
        chunk_size: int = 100,
    ) -> Iterator[Sequence[Any]]: ...
    def has_results(self) -> bool: ...
    @overload
    def execute_sql(
        self, result_type: Literal["cursor"] = "cursor", chunked_fetch: bool = False, chunk_size: int = 100
    ) -> CursorWrapper: ...
    @overload
    def execute_sql(
        self,
        result_type: Literal["no results"] | None = "no results",
        chunked_fetch: bool = False,
        chunk_size: int = 100,
    ) -> None: ...
    @overload
    def execute_sql(
        self, result_type: Literal["single"] = "single", chunked_fetch: bool = False, chunk_size: int = 100
    ) -> Iterable[Sequence[Any]] | None: ...
    @overload
    def execute_sql(
        self, result_type: Literal["multi"] = "multi", chunked_fetch: bool = False, chunk_size: int = 100
    ) -> Iterable[list[Sequence[Any]]] | None: ...
    def as_subquery_condition(self, alias: str, columns: list[str], compiler: SQLCompiler) -> _AsSqlType: ...
    def explain_query(self) -> Iterator[str]: ...

class SQLInsertCompiler(SQLCompiler):
    query: InsertQuery
    returning_fields: Sequence[Any] | None
    returning_params: Sequence[Any]
    def field_as_sql(self, field: Any, val: Any) -> _AsSqlType: ...
    def prepare_value(self, field: Any, value: Any) -> Any: ...
    def pre_save_val(self, field: Any, obj: Any) -> Any: ...
    def assemble_as_sql(self, fields: Any, value_rows: Any) -> tuple[list[list[str]], list[list[Any]]]: ...
    def as_sql(self) -> list[_AsSqlType]: ...  # type: ignore[override]
    def execute_sql(  # type: ignore[override]
        self, returning_fields: Sequence[str] | None = None
    ) -> list[tuple[Any]]: ...  # 1-tuple

class SQLDeleteCompiler(SQLCompiler):
    query: DeleteQuery
    @cached_property
    def single_alias(self) -> bool: ...
    @cached_property
    def contains_self_reference_subquery(self) -> bool: ...
    def as_sql(self) -> _AsSqlType: ...  # type: ignore[override]

class SQLUpdateCompiler(SQLCompiler):
    query: UpdateQuery
    def as_sql(self) -> _AsSqlType: ...  # type: ignore[override]
    def execute_sql(self, result_type: Literal["cursor", "no results"]) -> int: ...  # type: ignore[override]
    def pre_sql_setup(self) -> None: ...  # type: ignore[override]

class SQLAggregateCompiler(SQLCompiler):
    query: AggregateQuery
    col_count: int
    def as_sql(self) -> _AsSqlType: ...  # type: ignore[override]

def cursor_iter(
    cursor: CursorWrapper, sentinel: Any, col_count: int | None, itersize: int
) -> Iterator[list[Sequence[Any]]]: ...

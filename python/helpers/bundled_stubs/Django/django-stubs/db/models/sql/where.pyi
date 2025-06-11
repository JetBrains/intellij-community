from collections.abc import Sequence
from typing import Any

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.expressions import Expression
from django.db.models.fields import BooleanField
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType
from django.db.models.sql.query import Query
from django.utils import tree
from django.utils.functional import cached_property

AND: str
OR: str

class WhereNode(tree.Node):
    connector: str
    negated: bool
    default: str
    resolved: bool
    conditional: bool
    def split_having(self, negated: bool = ...) -> tuple[WhereNode | None, WhereNode | None]: ...
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...
    def get_group_by_cols(self, alias: str | None = ...) -> list[Expression]: ...
    def relabel_aliases(self, change_map: dict[str | None, str]) -> None: ...
    def clone(self) -> WhereNode: ...
    def relabeled_clone(self, change_map: dict[str | None, str]) -> WhereNode: ...
    def resolve_expression(self, *args: Any, **kwargs: Any) -> WhereNode: ...
    @cached_property
    def output_field(self) -> BooleanField: ...
    @cached_property
    def contains_aggregate(self) -> bool: ...
    @cached_property
    def contains_over_clause(self) -> bool: ...
    @property
    def is_summary(self) -> bool: ...

class NothingNode:
    contains_aggregate: bool
    def as_sql(
        self, compiler: SQLCompiler | None = None, connection: BaseDatabaseWrapper | None = None
    ) -> _AsSqlType: ...

class ExtraWhere:
    contains_aggregate: bool
    sqls: Sequence[str]
    params: Sequence[int] | Sequence[str] | None
    def __init__(self, sqls: Sequence[str], params: Sequence[int] | Sequence[str] | None) -> None: ...
    def as_sql(
        self, compiler: SQLCompiler | None = None, connection: BaseDatabaseWrapper | None = None
    ) -> _AsSqlType: ...

class SubqueryConstraint:
    contains_aggregate: bool
    alias: str
    columns: list[str]
    targets: list[str]
    query_object: Query
    def __init__(self, alias: str, columns: list[str], targets: list[str], query_object: Query) -> None: ...
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...

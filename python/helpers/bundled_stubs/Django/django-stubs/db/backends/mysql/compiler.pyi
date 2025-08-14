from typing import Any

from django.db.models.sql import compiler
from django.db.models.sql.compiler import _AsSqlType

class SQLCompiler(compiler.SQLCompiler):
    def as_subquery_condition(self, alias: Any, columns: Any, compiler: Any) -> _AsSqlType: ...

class SQLInsertCompiler(compiler.SQLInsertCompiler, SQLCompiler): ...

class SQLDeleteCompiler(compiler.SQLDeleteCompiler, SQLCompiler):
    # https://github.com/django/django/blob/242499f2dc2bf24a9a5c855690a2e13d3303581a/django/db/backends/mysql/compiler.py#L26
    def as_sql(self) -> _AsSqlType: ...  # type: ignore[override]

class SQLUpdateCompiler(compiler.SQLUpdateCompiler, SQLCompiler): ...
class SQLAggregateCompiler(compiler.SQLAggregateCompiler, SQLCompiler): ...

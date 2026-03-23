from typing import Any

from django.db.models.sql.compiler import SQLAggregateCompiler as SQLAggregateCompiler
from django.db.models.sql.compiler import SQLCompiler as BaseSQLCompiler
from django.db.models.sql.compiler import SQLDeleteCompiler as SQLDeleteCompiler
from django.db.models.sql.compiler import SQLInsertCompiler as BaseSQLInsertCompiler
from django.db.models.sql.compiler import SQLUpdateCompiler as SQLUpdateCompiler
from typing_extensions import override

__all__ = [
    "SQLAggregateCompiler",
    "SQLCompiler",
    "SQLDeleteCompiler",
    "SQLInsertCompiler",
    "SQLUpdateCompiler",
]

class InsertUnnest(list[str]): ...

class SQLCompiler(BaseSQLCompiler):
    @override
    def quote_name_unless_alias(self, name: str) -> str: ...

class SQLInsertCompiler(BaseSQLInsertCompiler):
    @override
    def assemble_as_sql(self, fields: Any, value_rows: Any) -> tuple[Any, Any]: ...

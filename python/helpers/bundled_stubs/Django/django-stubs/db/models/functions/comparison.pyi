import re
from typing import Any

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models import Func
from django.db.models.expressions import Combinable
from django.db.models.fields import Field
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType
from typing_extensions import override

class Cast(Func):
    def __init__(self, expression: Combinable | str, output_field: str | Field) -> None: ...
    @override
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...  # type: ignore[override]
    def as_mysql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...
    def as_postgresql(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any
    ) -> _AsSqlType: ...
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class Coalesce(Func):
    @property
    @override
    def empty_result_set_value(self) -> Any: ...
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class Collate(Func):
    collation_re: re.Pattern[str]
    def __init__(self, expression: Combinable | str, collation: str) -> None: ...
    @override
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...  # type: ignore[override]

class Greatest(Func): ...
class Least(Func): ...

class NullIf(Func):
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

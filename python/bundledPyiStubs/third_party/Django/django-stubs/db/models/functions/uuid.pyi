import datetime as dt
from typing import Any, ClassVar

from django.db import models
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.expressions import Func
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType
from typing_extensions import override

class UUID4(Func):
    output_field: ClassVar[models.UUIDField[Any, Any]]
    @override
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...  # type: ignore[override]
    def as_postgresql(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any
    ) -> _AsSqlType: ...
    def as_mysql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class UUID7(Func):
    output_field: ClassVar[models.UUIDField[Any, Any]]
    def __init__(self, shift: dt.timedelta | None = None, **extra: Any) -> None: ...
    @override
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...  # type: ignore[override]
    def as_postgresql(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any
    ) -> _AsSqlType: ...
    @override
    def as_sqlite(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...
    def as_mysql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

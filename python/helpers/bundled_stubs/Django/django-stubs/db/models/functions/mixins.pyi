from typing import Any

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType

class FixDecimalInputMixin:
    def as_postgresql(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any
    ) -> _AsSqlType: ...

class FixDurationInputMixin:
    def as_mysql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class NumericOutputFieldMixin: ...

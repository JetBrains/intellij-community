from collections.abc import Sequence
from typing import Any, ClassVar

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.expressions import Func
from django.db.models.fields.json import JSONField
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType

class JSONArray(Func):
    output_field: ClassVar[JSONField]
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...  # type: ignore [override]
    def as_native(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, *, returning: str, **extra_context: Any
    ) -> _AsSqlType: ...
    def as_postgresql(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any
    ) -> _AsSqlType: ...
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class JSONObject(Func):
    output_field: ClassVar[JSONField]
    def __init__(self, **fields: Any) -> None: ...
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...  # type: ignore [override]
    def as_native(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, *, returning: str, **extra_context: Any
    ) -> _AsSqlType: ...
    def as_postgresql(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any
    ) -> _AsSqlType: ...
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...
    def join(self, args: Sequence[Any]) -> str: ...

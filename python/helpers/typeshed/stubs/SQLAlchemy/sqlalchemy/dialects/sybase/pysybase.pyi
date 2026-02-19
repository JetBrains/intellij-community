#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any

from sqlalchemy import types as sqltypes
from sqlalchemy.dialects.sybase.base import SybaseDialect, SybaseExecutionContext, SybaseSQLCompiler

class _SybNumeric(sqltypes.Numeric):
    def result_processor(self, dialect, type_): ...

class SybaseExecutionContext_pysybase(SybaseExecutionContext):
    def set_ddl_autocommit(self, dbapi_connection, value) -> None: ...
    def pre_exec(self) -> None: ...

class SybaseSQLCompiler_pysybase(SybaseSQLCompiler):
    def bindparam_string(self, name, **kw): ...

class SybaseDialect_pysybase(SybaseDialect):
    driver: str
    statement_compiler: Any
    supports_statement_cache: bool
    colspecs: Any
    @classmethod
    def dbapi(cls): ...
    def create_connect_args(self, url): ...
    def do_executemany(self, cursor, statement, parameters, context: Any | None = ...) -> None: ...
    def is_disconnect(self, e, connection, cursor): ...

dialect = SybaseDialect_pysybase

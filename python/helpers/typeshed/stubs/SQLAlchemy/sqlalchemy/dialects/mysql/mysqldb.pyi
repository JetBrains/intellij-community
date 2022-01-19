from typing import Any

from ...util import memoized_property
from .base import MySQLCompiler, MySQLDialect, MySQLExecutionContext

class MySQLExecutionContext_mysqldb(MySQLExecutionContext):
    @property
    def rowcount(self): ...

class MySQLCompiler_mysqldb(MySQLCompiler): ...

class MySQLDialect_mysqldb(MySQLDialect):
    driver: str
    supports_statement_cache: bool
    supports_unicode_statements: bool
    supports_sane_rowcount: bool
    supports_sane_multi_rowcount: bool
    supports_native_decimal: bool
    default_paramstyle: str
    statement_compiler: Any
    preparer: Any
    def __init__(self, **kwargs) -> None: ...
    @memoized_property
    def supports_server_side_cursors(self): ...
    @classmethod
    def dbapi(cls): ...
    def on_connect(self): ...
    def do_ping(self, dbapi_connection): ...
    def do_executemany(self, cursor, statement, parameters, context: Any | None = ...) -> None: ...
    def create_connect_args(self, url, _translate_args: Any | None = ...): ...

dialect = MySQLDialect_mysqldb

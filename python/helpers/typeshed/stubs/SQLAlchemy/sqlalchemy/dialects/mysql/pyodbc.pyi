from typing import Any

from ...connectors.pyodbc import PyODBCConnector
from .base import MySQLDialect, MySQLExecutionContext
from .types import TIME

class _pyodbcTIME(TIME):
    def result_processor(self, dialect, coltype): ...

class MySQLExecutionContext_pyodbc(MySQLExecutionContext):
    def get_lastrowid(self): ...

class MySQLDialect_pyodbc(PyODBCConnector, MySQLDialect):
    supports_statement_cache: bool
    colspecs: Any
    supports_unicode_statements: bool
    pyodbc_driver_name: str
    def on_connect(self): ...

dialect = MySQLDialect_pyodbc

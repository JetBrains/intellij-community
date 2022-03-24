from typing import Any

from .base import BIT
from .mysqldb import MySQLDialect_mysqldb

class _cymysqlBIT(BIT):
    def result_processor(self, dialect, coltype): ...

class MySQLDialect_cymysql(MySQLDialect_mysqldb):
    driver: str
    supports_statement_cache: bool
    description_encoding: Any
    supports_sane_rowcount: bool
    supports_sane_multi_rowcount: bool
    supports_unicode_statements: bool
    colspecs: Any
    @classmethod
    def dbapi(cls): ...
    def is_disconnect(self, e, connection, cursor): ...

dialect = MySQLDialect_cymysql

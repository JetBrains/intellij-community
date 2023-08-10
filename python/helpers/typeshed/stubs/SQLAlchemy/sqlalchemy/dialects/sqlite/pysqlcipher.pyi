from typing import Any

from .pysqlite import SQLiteDialect_pysqlite

class SQLiteDialect_pysqlcipher(SQLiteDialect_pysqlite):
    driver: str
    supports_statement_cache: bool
    pragmas: Any
    @classmethod
    def dbapi(cls): ...
    @classmethod
    def get_pool_class(cls, url): ...
    def on_connect_url(self, url): ...
    def create_connect_args(self, url): ...

dialect = SQLiteDialect_pysqlcipher

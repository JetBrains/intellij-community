from typing import Any

from .base import MySQLDialect

class MariaDBDialect(MySQLDialect):
    is_mariadb: bool
    supports_statement_cache: bool
    name: str
    preparer: Any

def loader(driver): ...

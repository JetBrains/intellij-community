from typing import Any

from ...types import Numeric
from .base import MSDialect, MSIdentifierPreparer

class _MSNumeric_pymssql(Numeric):
    def result_processor(self, dialect, type_): ...

class MSIdentifierPreparer_pymssql(MSIdentifierPreparer):
    def __init__(self, dialect) -> None: ...

class MSDialect_pymssql(MSDialect):
    supports_statement_cache: bool
    supports_native_decimal: bool
    driver: str
    preparer: Any
    colspecs: Any
    @classmethod
    def dbapi(cls): ...
    def create_connect_args(self, url): ...
    def is_disconnect(self, e, connection, cursor): ...
    def set_isolation_level(self, connection, level) -> None: ...

dialect = MSDialect_pymssql

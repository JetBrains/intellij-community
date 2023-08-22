from typing import Any

from ...connectors.mxodbc import MxODBCConnector
from .base import VARBINARY, MSDialect, _MSDate, _MSTime
from .pyodbc import MSExecutionContext_pyodbc, _MSNumeric_pyodbc

class _MSNumeric_mxodbc(_MSNumeric_pyodbc): ...

class _MSDate_mxodbc(_MSDate):
    def bind_processor(self, dialect): ...

class _MSTime_mxodbc(_MSTime):
    def bind_processor(self, dialect): ...

class _VARBINARY_mxodbc(VARBINARY):
    def bind_processor(self, dialect): ...

class MSExecutionContext_mxodbc(MSExecutionContext_pyodbc): ...

class MSDialect_mxodbc(MxODBCConnector, MSDialect):
    supports_statement_cache: bool
    colspecs: Any
    description_encoding: Any
    def __init__(self, description_encoding: Any | None = ..., **params) -> None: ...

dialect = MSDialect_mxodbc

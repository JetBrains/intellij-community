from sqlalchemy.connectors.mxodbc import MxODBCConnector
from sqlalchemy.dialects.sybase.base import SybaseDialect, SybaseExecutionContext

class SybaseExecutionContext_mxodbc(SybaseExecutionContext): ...

class SybaseDialect_mxodbc(MxODBCConnector, SybaseDialect):
    supports_statement_cache: bool

dialect = SybaseDialect_mxodbc

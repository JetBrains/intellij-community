#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from sqlalchemy.connectors.mxodbc import MxODBCConnector
from sqlalchemy.dialects.sybase.base import SybaseDialect, SybaseExecutionContext

class SybaseExecutionContext_mxodbc(SybaseExecutionContext): ...

class SybaseDialect_mxodbc(MxODBCConnector, SybaseDialect):
    supports_statement_cache: bool

dialect = SybaseDialect_mxodbc

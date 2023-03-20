from ..dialects.firebird import base as firebird_base
from ..dialects.mssql import base as mssql_base
from ..dialects.mysql import base as mysql_base
from ..dialects.oracle import base as oracle_base
from ..dialects.postgresql import base as postgresql_base
from ..dialects.sqlite import base as sqlite_base
from ..dialects.sybase import base as sybase_base

__all__ = ("firebird", "mssql", "mysql", "postgresql", "sqlite", "oracle", "sybase")

firebird = firebird_base
mssql = mssql_base
mysql = mysql_base
oracle = oracle_base
postgresql = postgresql_base
postgres = postgresql_base
sqlite = sqlite_base
sybase = sybase_base

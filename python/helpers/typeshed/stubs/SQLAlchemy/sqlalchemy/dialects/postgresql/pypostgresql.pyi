from typing import Any

import sqlalchemy.types as sqltypes

from ...util import memoized_property
from .base import PGDialect, PGExecutionContext

class PGNumeric(sqltypes.Numeric):
    def bind_processor(self, dialect): ...
    def result_processor(self, dialect, coltype): ...

class PGExecutionContext_pypostgresql(PGExecutionContext): ...

class PGDialect_pypostgresql(PGDialect):
    driver: str
    supports_statement_cache: bool
    supports_unicode_statements: bool
    supports_unicode_binds: bool
    description_encoding: Any
    default_paramstyle: str
    supports_sane_rowcount: bool
    supports_sane_multi_rowcount: bool
    colspecs: Any
    @classmethod
    def dbapi(cls): ...
    @memoized_property
    def dbapi_exception_translation_map(self): ...
    def create_connect_args(self, url): ...
    def is_disconnect(self, e, connection, cursor): ...

dialect = PGDialect_pypostgresql

from typing import Any

import sqlalchemy.types as sqltypes

from .array import ARRAY as PGARRAY
from .base import ENUM, UUID, PGCompiler, PGDialect, PGExecutionContext, PGIdentifierPreparer
from .hstore import HSTORE
from .json import JSON, JSONB

logger: Any

class _PGNumeric(sqltypes.Numeric):
    def bind_processor(self, dialect) -> None: ...
    def result_processor(self, dialect, coltype): ...

class _PGEnum(ENUM):
    def result_processor(self, dialect, coltype): ...

class _PGHStore(HSTORE):
    def bind_processor(self, dialect): ...
    def result_processor(self, dialect, coltype): ...

class _PGARRAY(PGARRAY):
    def bind_expression(self, bindvalue): ...

class _PGJSON(JSON):
    def result_processor(self, dialect, coltype) -> None: ...

class _PGJSONB(JSONB):
    def result_processor(self, dialect, coltype) -> None: ...

class _PGUUID(UUID):
    def bind_processor(self, dialect): ...
    def result_processor(self, dialect, coltype): ...

class PGExecutionContext_psycopg2(PGExecutionContext):
    def create_server_side_cursor(self): ...
    cursor_fetch_strategy: Any
    def post_exec(self) -> None: ...

class PGCompiler_psycopg2(PGCompiler): ...
class PGIdentifierPreparer_psycopg2(PGIdentifierPreparer): ...

EXECUTEMANY_PLAIN: Any
EXECUTEMANY_BATCH: Any
EXECUTEMANY_VALUES: Any
EXECUTEMANY_VALUES_PLUS_BATCH: Any

class PGDialect_psycopg2(PGDialect):
    driver: str
    supports_statement_cache: bool
    supports_unicode_statements: bool
    supports_server_side_cursors: bool
    default_paramstyle: str
    supports_sane_multi_rowcount: bool
    statement_compiler: Any
    preparer: Any
    psycopg2_version: Any
    engine_config_types: Any
    colspecs: Any
    use_native_unicode: Any
    use_native_hstore: Any
    use_native_uuid: Any
    supports_unicode_binds: Any
    client_encoding: Any
    executemany_mode: Any
    insert_executemany_returning: bool
    executemany_batch_page_size: Any
    executemany_values_page_size: Any
    def __init__(
        self,
        use_native_unicode: bool = ...,
        client_encoding: Any | None = ...,
        use_native_hstore: bool = ...,
        use_native_uuid: bool = ...,
        executemany_mode: str = ...,
        executemany_batch_page_size: int = ...,
        executemany_values_page_size: int = ...,
        **kwargs,
    ) -> None: ...
    def initialize(self, connection) -> None: ...
    @classmethod
    def dbapi(cls): ...
    def set_isolation_level(self, connection, level) -> None: ...
    def set_readonly(self, connection, value) -> None: ...
    def get_readonly(self, connection): ...
    def set_deferrable(self, connection, value) -> None: ...
    def get_deferrable(self, connection): ...
    def do_ping(self, dbapi_connection): ...
    def on_connect(self): ...
    def do_executemany(self, cursor, statement, parameters, context: Any | None = ...) -> None: ...
    def create_connect_args(self, url): ...
    def is_disconnect(self, e, connection, cursor): ...

dialect = PGDialect_psycopg2

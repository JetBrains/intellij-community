from typing import Any

from sqlalchemy import types as sqltypes
from sqlalchemy.engine import default, reflection
from sqlalchemy.sql import compiler
from sqlalchemy.types import (
    BIGINT as BIGINT,
    BINARY as BINARY,
    CHAR as CHAR,
    DATE as DATE,
    DATETIME as DATETIME,
    DECIMAL as DECIMAL,
    FLOAT as FLOAT,
    INT as INT,
    INTEGER as INTEGER,
    NCHAR as NCHAR,
    NUMERIC as NUMERIC,
    NVARCHAR as NVARCHAR,
    REAL as REAL,
    SMALLINT as SMALLINT,
    TEXT as TEXT,
    TIME as TIME,
    TIMESTAMP as TIMESTAMP,
    VARBINARY as VARBINARY,
    VARCHAR as VARCHAR,
    Unicode as Unicode,
)

RESERVED_WORDS: Any

class _SybaseUnitypeMixin:
    def result_processor(self, dialect, coltype): ...

class UNICHAR(_SybaseUnitypeMixin, sqltypes.Unicode):
    __visit_name__: str

class UNIVARCHAR(_SybaseUnitypeMixin, sqltypes.Unicode):
    __visit_name__: str

class UNITEXT(_SybaseUnitypeMixin, sqltypes.UnicodeText):
    __visit_name__: str

class TINYINT(sqltypes.Integer):
    __visit_name__: str

class BIT(sqltypes.TypeEngine):
    __visit_name__: str

class MONEY(sqltypes.TypeEngine):
    __visit_name__: str

class SMALLMONEY(sqltypes.TypeEngine):
    __visit_name__: str

class UNIQUEIDENTIFIER(sqltypes.TypeEngine):
    __visit_name__: str

class IMAGE(sqltypes.LargeBinary):
    __visit_name__: str

class SybaseTypeCompiler(compiler.GenericTypeCompiler):
    def visit_large_binary(self, type_, **kw): ...
    def visit_boolean(self, type_, **kw): ...
    def visit_unicode(self, type_, **kw): ...
    def visit_UNICHAR(self, type_, **kw): ...
    def visit_UNIVARCHAR(self, type_, **kw): ...
    def visit_UNITEXT(self, type_, **kw): ...
    def visit_TINYINT(self, type_, **kw): ...
    def visit_IMAGE(self, type_, **kw): ...
    def visit_BIT(self, type_, **kw): ...
    def visit_MONEY(self, type_, **kw): ...
    def visit_SMALLMONEY(self, type_, **kw): ...
    def visit_UNIQUEIDENTIFIER(self, type_, **kw): ...

ischema_names: Any

class SybaseInspector(reflection.Inspector):
    def __init__(self, conn) -> None: ...
    def get_table_id(self, table_name, schema: Any | None = ...): ...

class SybaseExecutionContext(default.DefaultExecutionContext):
    def set_ddl_autocommit(self, connection, value) -> None: ...
    def pre_exec(self) -> None: ...
    def post_exec(self) -> None: ...
    def get_lastrowid(self): ...

class SybaseSQLCompiler(compiler.SQLCompiler):
    ansi_bind_rules: bool
    extract_map: Any
    def get_from_hint_text(self, table, text): ...
    def limit_clause(self, select, **kw): ...
    def visit_extract(self, extract, **kw): ...
    def visit_now_func(self, fn, **kw): ...
    def for_update_clause(self, select): ...
    def order_by_clause(self, select, **kw): ...
    def delete_table_clause(self, delete_stmt, from_table, extra_froms): ...
    def delete_extra_from_clause(self, delete_stmt, from_table, extra_froms, from_hints, **kw): ...

class SybaseDDLCompiler(compiler.DDLCompiler):
    def get_column_specification(self, column, **kwargs): ...
    def visit_drop_index(self, drop): ...

class SybaseIdentifierPreparer(compiler.IdentifierPreparer):
    reserved_words: Any

class SybaseDialect(default.DefaultDialect):
    name: str
    supports_unicode_statements: bool
    supports_sane_rowcount: bool
    supports_sane_multi_rowcount: bool
    supports_statement_cache: bool
    supports_native_boolean: bool
    supports_unicode_binds: bool
    postfetch_lastrowid: bool
    colspecs: Any
    ischema_names: Any
    type_compiler: Any
    statement_compiler: Any
    ddl_compiler: Any
    preparer: Any
    inspector: Any
    construct_arguments: Any
    def __init__(self, *args, **kwargs) -> None: ...
    max_identifier_length: int
    def initialize(self, connection) -> None: ...
    def get_table_id(self, connection, table_name, schema: Any | None = ..., **kw): ...
    def get_columns(self, connection, table_name, schema: Any | None = ..., **kw): ...
    def get_foreign_keys(self, connection, table_name, schema: Any | None = ..., **kw): ...
    def get_indexes(self, connection, table_name, schema: Any | None = ..., **kw): ...
    def get_pk_constraint(self, connection, table_name, schema: Any | None = ..., **kw): ...
    def get_schema_names(self, connection, **kw): ...
    def get_table_names(self, connection, schema: Any | None = ..., **kw): ...
    def get_view_definition(self, connection, view_name, schema: Any | None = ..., **kw): ...
    def get_view_names(self, connection, schema: Any | None = ..., **kw): ...
    def has_table(self, connection, table_name, schema: Any | None = ...): ...  # type: ignore[override]

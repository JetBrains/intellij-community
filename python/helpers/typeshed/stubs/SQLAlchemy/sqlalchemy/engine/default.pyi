from typing import Any, ClassVar

from .. import types as sqltypes
from ..util import memoized_property
from . import interfaces

AUTOCOMMIT_REGEXP: Any
SERVER_SIDE_CURSOR_RE: Any
CACHE_HIT: Any
CACHE_MISS: Any
CACHING_DISABLED: Any
NO_CACHE_KEY: Any
NO_DIALECT_SUPPORT: Any

class DefaultDialect(interfaces.Dialect):  # type: ignore[misc]
    execution_ctx_cls: ClassVar[type[interfaces.ExecutionContext]]
    statement_compiler: Any
    ddl_compiler: Any
    type_compiler: Any
    preparer: Any
    supports_alter: bool
    supports_comments: bool
    inline_comments: bool
    use_setinputsizes: bool
    supports_statement_cache: bool
    default_sequence_base: int
    execute_sequence_format: Any
    supports_schemas: bool
    supports_views: bool
    supports_sequences: bool
    sequences_optional: bool
    preexecute_autoincrement_sequences: bool
    supports_identity_columns: bool
    postfetch_lastrowid: bool
    implicit_returning: bool
    full_returning: bool
    insert_executemany_returning: bool
    cte_follows_insert: bool
    supports_native_enum: bool
    supports_native_boolean: bool
    non_native_boolean_check_constraint: bool
    supports_simple_order_by_label: bool
    tuple_in_values: bool
    connection_characteristics: Any
    engine_config_types: Any
    supports_native_decimal: bool
    supports_unicode_statements: bool
    supports_unicode_binds: bool
    returns_unicode_strings: Any
    description_encoding: Any
    name: str
    max_identifier_length: int
    isolation_level: Any
    max_index_name_length: Any
    max_constraint_name_length: Any
    supports_sane_rowcount: bool
    supports_sane_multi_rowcount: bool
    colspecs: Any
    default_paramstyle: str
    supports_default_values: bool
    supports_default_metavalue: bool
    supports_empty_insert: bool
    supports_multivalues_insert: bool
    supports_is_distinct_from: bool
    supports_server_side_cursors: bool
    server_side_cursors: bool
    supports_for_update_of: bool
    server_version_info: Any
    default_schema_name: Any
    construct_arguments: Any
    requires_name_normalize: bool
    reflection_options: Any
    dbapi_exception_translation_map: Any
    is_async: bool
    CACHE_HIT: Any
    CACHE_MISS: Any
    CACHING_DISABLED: Any
    NO_CACHE_KEY: Any
    NO_DIALECT_SUPPORT: Any
    convert_unicode: Any
    encoding: Any
    positional: bool
    dbapi: Any
    paramstyle: Any
    identifier_preparer: Any
    case_sensitive: Any
    label_length: Any
    compiler_linting: Any
    def __init__(
        self,
        convert_unicode: bool = ...,
        encoding: str = ...,
        paramstyle: Any | None = ...,
        dbapi: Any | None = ...,
        implicit_returning: Any | None = ...,
        case_sensitive: bool = ...,
        supports_native_boolean: Any | None = ...,
        max_identifier_length: Any | None = ...,
        label_length: Any | None = ...,
        compiler_linting=...,
        server_side_cursors: bool = ...,
        **kwargs,
    ) -> None: ...
    @property
    def dialect_description(self): ...
    @property
    def supports_sane_rowcount_returning(self): ...
    @classmethod
    def get_pool_class(cls, url): ...
    def get_dialect_pool_class(self, url): ...
    @classmethod
    def load_provisioning(cls) -> None: ...
    default_isolation_level: Any
    def initialize(self, connection) -> None: ...
    def on_connect(self) -> None: ...
    def get_default_isolation_level(self, dbapi_conn): ...
    def type_descriptor(self, typeobj): ...
    def has_index(self, connection, table_name, index_name, schema: Any | None = ...): ...
    def validate_identifier(self, ident) -> None: ...
    def connect(self, *cargs, **cparams): ...
    def create_connect_args(self, url): ...
    def set_engine_execution_options(self, engine, opts) -> None: ...
    def set_connection_execution_options(self, connection, opts) -> None: ...
    def do_begin(self, dbapi_connection) -> None: ...
    def do_rollback(self, dbapi_connection) -> None: ...
    def do_commit(self, dbapi_connection) -> None: ...
    def do_close(self, dbapi_connection) -> None: ...
    def do_ping(self, dbapi_connection): ...
    def create_xid(self): ...
    def do_savepoint(self, connection, name) -> None: ...
    def do_rollback_to_savepoint(self, connection, name) -> None: ...
    def do_release_savepoint(self, connection, name) -> None: ...
    def do_executemany(self, cursor, statement, parameters, context: Any | None = ...) -> None: ...
    def do_execute(self, cursor, statement, parameters, context: Any | None = ...) -> None: ...
    def do_execute_no_params(self, cursor, statement, context: Any | None = ...) -> None: ...  # type: ignore[override]
    def is_disconnect(self, e, connection, cursor): ...
    def reset_isolation_level(self, dbapi_conn) -> None: ...
    def normalize_name(self, name): ...
    def denormalize_name(self, name): ...
    def get_driver_connection(self, connection): ...

class _RendersLiteral:
    def literal_processor(self, dialect): ...

class _StrDateTime(_RendersLiteral, sqltypes.DateTime): ...
class _StrDate(_RendersLiteral, sqltypes.Date): ...
class _StrTime(_RendersLiteral, sqltypes.Time): ...

class StrCompileDialect(DefaultDialect):  # type: ignore[misc]
    statement_compiler: Any
    ddl_compiler: Any
    type_compiler: Any
    preparer: Any
    supports_statement_cache: bool
    supports_identity_columns: bool
    supports_sequences: bool
    sequences_optional: bool
    preexecute_autoincrement_sequences: bool
    implicit_returning: bool
    supports_native_boolean: bool
    supports_multivalues_insert: bool
    supports_simple_order_by_label: bool
    colspecs: Any

class DefaultExecutionContext(interfaces.ExecutionContext):
    isinsert: bool
    isupdate: bool
    isdelete: bool
    is_crud: bool
    is_text: bool
    isddl: bool
    executemany: bool
    compiled: Any
    statement: Any
    result_column_struct: Any
    returned_default_rows: Any
    execution_options: Any
    include_set_input_sizes: Any
    exclude_set_input_sizes: Any
    cursor_fetch_strategy: Any
    cache_stats: Any
    invoked_statement: Any
    cache_hit: Any
    @memoized_property
    def identifier_preparer(self): ...
    @memoized_property
    def engine(self): ...
    @memoized_property
    def postfetch_cols(self): ...
    @memoized_property
    def prefetch_cols(self): ...
    @memoized_property
    def returning_cols(self) -> None: ...
    @memoized_property
    def no_parameters(self): ...
    @memoized_property
    def should_autocommit(self): ...
    @property
    def connection(self): ...
    def should_autocommit_text(self, statement): ...
    def create_cursor(self): ...
    def create_default_cursor(self): ...
    def create_server_side_cursor(self) -> None: ...
    def pre_exec(self) -> None: ...
    def get_out_parameter_values(self, names) -> None: ...
    def post_exec(self) -> None: ...
    def get_result_processor(self, type_, colname, coltype): ...
    def get_lastrowid(self): ...
    def handle_dbapi_exception(self, e) -> None: ...
    @property
    def rowcount(self): ...
    def supports_sane_rowcount(self): ...
    def supports_sane_multi_rowcount(self): ...
    @memoized_property
    def inserted_primary_key_rows(self): ...
    def lastrow_has_defaults(self): ...
    current_parameters: Any
    def get_current_parameters(self, isolate_multiinsert_groups: bool = ...): ...
    def get_insert_default(self, column): ...
    def get_update_default(self, column): ...

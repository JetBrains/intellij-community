from collections.abc import Container, Iterator
from typing import Any, Literal

from django.db.backends.base.base import BaseDatabaseWrapper
from typing_extensions import override

from .client import DatabaseClient
from .creation import DatabaseCreation
from .features import DatabaseFeatures
from .introspection import DatabaseIntrospection
from .operations import DatabaseOperations
from .validation import DatabaseValidation

version: Any
django_conversions: Any
server_version_re: Any

class CursorWrapper:
    codes_for_integrityerror: Any
    cursor: Any
    def __init__(self, cursor: Any) -> None: ...
    def execute(self, query: Any, args: Any | None = ...) -> Any: ...
    def executemany(self, query: Any, args: Any) -> Any: ...
    def __getattr__(self, attr: Any) -> Any: ...
    def __iter__(self) -> Iterator[Any]: ...

class DatabaseWrapper(BaseDatabaseWrapper):
    client: DatabaseClient
    creation: DatabaseCreation
    features: DatabaseFeatures
    introspection: DatabaseIntrospection
    validation: DatabaseValidation
    ops: DatabaseOperations

    client_class: type[DatabaseClient]
    creation_class: type[DatabaseCreation]
    features_class: type[DatabaseFeatures]
    introspection_class: type[DatabaseIntrospection]
    ops_class: type[DatabaseOperations]
    validation_class: type[DatabaseValidation]

    vendor: str
    data_types: Any
    operators: Any
    pattern_esc: str
    pattern_ops: Any
    isolation_levels: Any
    Database: Any
    SchemaEditorClass: Any
    isolation_level: Any
    @override
    def get_connection_params(self) -> dict[str, Any]: ...
    @override
    def get_new_connection(self, conn_params: Any) -> Any: ...
    @override
    def init_connection_state(self) -> None: ...
    @override
    def create_cursor(self, name: Any | None = ...) -> CursorWrapper: ...
    @override
    def disable_constraint_checking(self) -> Literal[True]: ...
    needs_rollback: Any
    @override
    def enable_constraint_checking(self) -> None: ...
    @override
    def check_constraints(self, table_names: Any | None = ...) -> None: ...
    @override
    def is_usable(self) -> bool: ...
    @property
    @override
    def display_name(self) -> str: ...  # type: ignore[override]
    @property
    @override
    def data_type_check_constraints(self) -> dict[str, str]: ...  # type: ignore[override]
    @property
    def mysql_server_data(self) -> dict[str, Any]: ...
    @property
    def mysql_server_info(self) -> str: ...
    @property
    def mysql_version(self) -> tuple[int, ...]: ...
    @property
    def mysql_is_mariadb(self) -> bool: ...
    @property
    def sql_mode(self) -> Container[str]: ...

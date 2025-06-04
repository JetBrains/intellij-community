from collections.abc import Generator, Iterator
from contextlib import contextmanager
from typing import Any

from django.db.backends.base.base import BaseDatabaseWrapper

from .client import DatabaseClient
from .creation import DatabaseCreation
from .features import DatabaseFeatures
from .introspection import DatabaseIntrospection
from .operations import DatabaseOperations
from .validation import DatabaseValidation

@contextmanager
def wrap_oracle_errors() -> Generator[None, None, None]: ...

class _UninitializedOperatorsDescriptor:
    def __get__(self, instance: Any, cls: Any | None = None) -> Any: ...

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
    display_name: str
    data_types: Any
    data_type_check_constraints: Any
    operators: Any
    pattern_esc: str
    Database: Any
    SchemaEditorClass: Any
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    def get_connection_params(self) -> Any: ...
    def get_new_connection(self, conn_params: Any) -> Any: ...
    pattern_ops: Any
    def init_connection_state(self) -> None: ...
    def create_cursor(self, name: Any | None = None) -> Any: ...
    def check_constraints(self, table_names: Any | None = None) -> None: ...
    def is_usable(self) -> Any: ...
    @property
    def oracle_version(self) -> Any: ...

class OracleParam:
    force_bytes: Any
    input_size: Any
    def __init__(self, param: Any, cursor: Any, strings_only: bool = False) -> None: ...

class VariableWrapper:
    var: Any
    def __init__(self, var: Any) -> None: ...
    def bind_parameter(self, cursor: Any) -> Any: ...
    def __getattr__(self, key: Any) -> Any: ...
    def __setattr__(self, key: Any, value: Any) -> None: ...

class FormatStylePlaceholderCursor:
    charset: str
    cursor: Any
    database: BaseDatabaseWrapper
    def __init__(self, connection: Any, database: BaseDatabaseWrapper) -> None: ...
    def execute(self, query: Any, params: Any | None = None) -> Any: ...
    def executemany(self, query: Any, params: Any | None = None) -> Any: ...
    def close(self) -> None: ...
    def var(self, *args: Any) -> Any: ...
    def arrayvar(self, *args: Any) -> Any: ...
    def __getattr__(self, attr: Any) -> Any: ...
    def __iter__(self) -> Iterator[Any]: ...

from io import IOBase
from typing import Any

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.backends.utils import CursorDebugWrapper as BaseCursorDebugWrapper
from django.db.backends.utils import _ExecuteQuery
from django.utils.functional import cached_property

from .client import DatabaseClient
from .creation import DatabaseCreation
from .features import DatabaseFeatures
from .introspection import DatabaseIntrospection
from .operations import DatabaseOperations

def psycopg2_version() -> tuple[int, ...]: ...

PSYCOPG2_VERSION: tuple[int, ...]

class DatabaseWrapper(BaseDatabaseWrapper):
    client: DatabaseClient
    creation: DatabaseCreation
    features: DatabaseFeatures
    introspection: DatabaseIntrospection
    ops: DatabaseOperations

    client_class: type[DatabaseClient]
    creation_class: type[DatabaseCreation]
    features_class: type[DatabaseFeatures]
    introspection_class: type[DatabaseIntrospection]
    ops_class: type[DatabaseOperations]

    operators: dict[str, str]
    pattern_esc: str
    pattern_ops: dict[str, str]

    # PostgreSQL backend-specific attributes.
    _named_cursor_idx: int
    @cached_property
    def pg_version(self) -> int: ...

class CursorDebugWrapper(BaseCursorDebugWrapper):
    def copy_expert(self, sql: _ExecuteQuery, file: IOBase, *args: Any) -> Any: ...
    def copy_to(self, file: IOBase, table: str, *args: Any, **kwargs: Any) -> Any: ...

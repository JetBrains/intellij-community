from types import ModuleType
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

TIMESTAMPTZ_OID: int

def psycopg_version() -> tuple[int, int, int]: ...

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

    Database: ModuleType
    operators: dict[str, str]
    pattern_esc: str
    pattern_ops: dict[str, str]

    # PostgreSQL backend-specific attributes.
    _named_cursor_idx: int
    @property
    def pool(self) -> Any: ...
    def close_pool(self) -> None: ...
    def tzinfo_factory(self, offset: int) -> Any: ...
    @cached_property
    def pg_version(self) -> int: ...

class CursorMixin:
    def callproc(self, name: Any, args: Any = ...) -> Any: ...

class ServerBindingCursor(CursorMixin): ...
class Cursor(CursorMixin): ...
class ServerSideCursor(CursorMixin): ...

class CursorDebugWrapper(BaseCursorDebugWrapper):
    def copy(self, statement: _ExecuteQuery) -> Any: ...

from collections.abc import Callable
from sqlite3 import dbapi2 as Database
from typing import Any, TypeVar

from django.db.backends.base.base import BaseDatabaseWrapper

from .client import DatabaseClient
from .creation import DatabaseCreation
from .features import DatabaseFeatures
from .introspection import DatabaseIntrospection
from .operations import DatabaseOperations

_R = TypeVar("_R")

def decoder(conv_func: Callable[[str], _R]) -> Callable[[bytes], _R]: ...

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

    def is_in_memory_db(self) -> bool: ...

FORMAT_QMARK_REGEX: Any

class SQLiteCursorWrapper(Database.Cursor): ...

def check_sqlite_version() -> None: ...

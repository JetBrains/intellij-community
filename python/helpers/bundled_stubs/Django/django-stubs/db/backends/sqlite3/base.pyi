from collections.abc import Callable, Iterable
from datetime import date, datetime
from sqlite3 import dbapi2 as Database
from types import ModuleType
from typing import Any, TypeVar

from django.db.backends.base.base import BaseDatabaseWrapper
from typing_extensions import override

from .client import DatabaseClient
from .creation import DatabaseCreation
from .features import DatabaseFeatures
from .introspection import DatabaseIntrospection
from .operations import DatabaseOperations
from .schema import DatabaseSchemaEditor

_R = TypeVar("_R")

def decoder(conv_func: Callable[[str], _R]) -> Callable[[bytes], _R]: ...
def adapt_date(val: date) -> str: ...
def adapt_datetime(val: datetime) -> str: ...

class DatabaseWrapper(BaseDatabaseWrapper):
    client: DatabaseClient
    creation: DatabaseCreation
    features: DatabaseFeatures
    introspection: DatabaseIntrospection
    ops: DatabaseOperations
    operators: dict[str, str]

    pattern_esc: str
    pattern_ops: dict[str, str]
    transaction_modes: frozenset[str]
    Database: ModuleType
    SchemaEditorClass: type[DatabaseSchemaEditor]
    client_class: type[DatabaseClient]
    creation_class: type[DatabaseCreation]
    features_class: type[DatabaseFeatures]
    introspection_class: type[DatabaseIntrospection]
    ops_class: type[DatabaseOperations]

    def is_in_memory_db(self) -> bool: ...

FORMAT_QMARK_REGEX: Any

class SQLiteCursorWrapper(Database.Cursor):
    @override
    def execute(self, query: str, params: Iterable[Any] | None = None) -> SQLiteCursorWrapper: ...  # type: ignore[override]
    @override
    def executemany(self, query: str, param_list: Iterable[Iterable[Any]]) -> SQLiteCursorWrapper: ...  # type: ignore[override]
    def convert_query(self, query: str, *, param_names: Any = None) -> str: ...

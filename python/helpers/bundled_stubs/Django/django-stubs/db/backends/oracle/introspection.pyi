from typing import Any, NamedTuple

from django.db.backends.base.introspection import BaseDatabaseIntrospection
from django.db.backends.oracle.base import DatabaseWrapper

FieldInfo: Any

class TableInfo(NamedTuple):
    name: str
    type: str
    comment: str | None

class DatabaseIntrospection(BaseDatabaseIntrospection):
    connection: DatabaseWrapper
    cache_bust_counter: int
    data_types_reverse: dict[int, str]

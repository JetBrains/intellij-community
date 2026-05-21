from typing import NamedTuple

from django.db.backends.base.introspection import BaseDatabaseIntrospection
from django.db.backends.postgresql.base import DatabaseWrapper

class FieldInfo(NamedTuple):
    name: str
    type_code: int
    display_size: int | None
    internal_size: int | None
    precision: int | None
    scale: int | None
    null_ok: bool
    default: str | None
    collation: str | None
    is_autofield: bool
    comment: str | None

class TableInfo(NamedTuple):
    name: str
    type: str
    comment: str | None

class DatabaseIntrospection(BaseDatabaseIntrospection):
    connection: DatabaseWrapper
    data_types_reverse: dict[int, str]
    index_default_access_method: str
    ignored_tables: list[str]

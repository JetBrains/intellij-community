from typing import Any, NamedTuple

from django.db.backends.base.introspection import BaseDatabaseIntrospection
from django.db.backends.sqlite3.base import DatabaseWrapper

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
    pk: bool
    has_json_constraint: bool

field_size_re: Any

def get_field_size(name: str) -> int | None: ...

class FlexibleFieldLookupDict:
    base_data_types_reverse: Any
    def __getitem__(self, key: str) -> Any: ...

class DatabaseIntrospection(BaseDatabaseIntrospection):
    connection: DatabaseWrapper

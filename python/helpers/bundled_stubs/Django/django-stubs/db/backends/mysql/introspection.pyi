from typing import Any, NamedTuple

from django.db.backends.base.introspection import BaseDatabaseIntrospection
from django.db.backends.mysql.base import DatabaseWrapper

FieldInfo: Any

class InfoLine(NamedTuple):
    col_name: str
    data_type: str
    max_len: int | None
    num_prec: int | None
    num_scale: int | None
    extra: str
    column_default: str | None
    collation: str | None
    is_unsigned: bool
    comment: str

class TableInfo(NamedTuple):
    name: str
    type: str
    comment: str

class DatabaseIntrospection(BaseDatabaseIntrospection):
    connection: DatabaseWrapper
    data_types_reverse: Any
    def get_storage_engine(self, cursor: Any, table_name: str) -> str: ...

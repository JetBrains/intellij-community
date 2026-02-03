from typing import Any, NamedTuple

from django.db.backends.base.introspection import BaseDatabaseIntrospection
from django.db.backends.mysql.base import DatabaseWrapper

FieldInfo: Any

class InfoLine(NamedTuple):
    col_name: str
    data_type: str
    max_len: int
    num_prec: int
    num_scale: int
    extra: str
    column_default: str
    collation: str | None
    is_unsigned: bool

class DatabaseIntrospection(BaseDatabaseIntrospection):
    connection: DatabaseWrapper
    data_types_reverse: Any

from typing import Any, NamedTuple

from django.db.backends.base.introspection import BaseDatabaseIntrospection
from django.db.backends.oracle.base import DatabaseWrapper
from django.db.backends.utils import CursorWrapper
from typing_extensions import override

FieldInfo: Any

class TableInfo(NamedTuple):
    name: str
    type: str
    comment: str | None

class DatabaseIntrospection(BaseDatabaseIntrospection):
    connection: DatabaseWrapper
    cache_bust_counter: int
    data_types_reverse: dict[int, str]

    @override
    def get_table_description(self, cursor: CursorWrapper, table_name: str) -> list[FieldInfo]: ...

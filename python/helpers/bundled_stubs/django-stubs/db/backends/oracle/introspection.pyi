from typing import Any

from django.db.backends.base.introspection import BaseDatabaseIntrospection
from django.db.backends.oracle.base import DatabaseWrapper

FieldInfo: Any

class DatabaseIntrospection(BaseDatabaseIntrospection):
    connection: DatabaseWrapper
    cache_bust_counter: int
    @property
    def data_types_reverse(self) -> Any: ...

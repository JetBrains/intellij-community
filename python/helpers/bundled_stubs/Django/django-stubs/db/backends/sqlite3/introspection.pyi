from typing import Any

from django.db.backends.base.introspection import BaseDatabaseIntrospection
from django.db.backends.sqlite3.base import DatabaseWrapper

field_size_re: Any

def get_field_size(name: str) -> int | None: ...

class FlexibleFieldLookupDict:
    base_data_types_reverse: Any
    def __getitem__(self, key: str) -> Any: ...

class DatabaseIntrospection(BaseDatabaseIntrospection):
    connection: DatabaseWrapper

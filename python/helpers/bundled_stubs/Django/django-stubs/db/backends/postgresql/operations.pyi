from types import ModuleType
from typing import Any

from django.db.backends.base.operations import BaseDatabaseOperations
from django.db.backends.postgresql.base import DatabaseWrapper

def get_json_dumps(encoder: type[Any] | None) -> Any: ...

class DatabaseOperations(BaseDatabaseOperations):
    connection: DatabaseWrapper
    explain_options: frozenset[str]
    integerfield_type_map: dict[str, type[Any]]
    numeric: ModuleType

    def compose_sql(self, sql: Any, params: Any) -> Any: ...

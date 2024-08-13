from typing import Any

from django.db.backends.postgresql.base import DatabaseWrapper as Psycopg2DatabaseWrapper

class DatabaseWrapper(Psycopg2DatabaseWrapper):
    SchemaEditorClass: Any
    features: Any
    ops: Any
    introspection: Any
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    def prepare_database(self) -> None: ...

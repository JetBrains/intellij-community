from typing import Any

from django.db.backends.base.introspection import BaseDatabaseIntrospection
from django.db.backends.postgresql.base import DatabaseWrapper

class DatabaseIntrospection(BaseDatabaseIntrospection):
    connection: DatabaseWrapper
    data_types_reverse: Any
    ignored_tables: Any
